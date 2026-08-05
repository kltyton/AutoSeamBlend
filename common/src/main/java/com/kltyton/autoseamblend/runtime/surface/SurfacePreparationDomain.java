package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.discovery.SurfaceRepresentativeFacts;
import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.inference.ConnectionAxis;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 中文：冻结模型面之后的通用表面候选整理、代表面合并和推断事实规划。
 *
 * English: Loader-neutral surface-candidate grouping, representative merging, and inference
 * fact planning after model faces have been frozen.
 */
public final class SurfacePreparationDomain {
    private SurfacePreparationDomain() {}

    /**
     * 中文：从冻结状态计算唯一表面和候选证据；顺序来自 Loader 的稳定冻结输入。
     *
     * English: Computes unique surfaces and candidate evidence from a frozen state. Ordering is
     * inherited from the Loader's stable frozen input.
     */
    public static StateInspection inspect(StateInput state) {
        StateInput input = Objects.requireNonNull(state, "state");
        ArrayList<String> evidence = new ArrayList<>(input.evidence());
        if (input.faces().isEmpty()) {
            evidence.add("NO_RESOLVED_CUBOID_SURFACES");
            return StateInspection.unresolved(input.identity(), evidence);
        }

        List<FaceInput> faces = aggregateExactContributors(input.faces());
        Set<String> sprites = faces.stream()
                .map(face -> face.source().spriteId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<SurfaceFace> directions = faces.stream()
                .map(FaceInput::face)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean spriteConsistent = sprites.size() == 1;
        boolean topOnly = directions.equals(Set.of(SurfaceFace.UP));
        FactState geometryEvidence = input.completeModelGeometry()
                ? FactState.TRUE
                : FactState.UNKNOWN;
        FactState surfaceEvidence = input.completeSurfaceEvidence()
                ? FactState.TRUE
                : FactState.UNKNOWN;
        List<InspectedSurface> surfaces = faces.stream()
                .map(face -> finish(
                        input.identity(),
                        face,
                        spriteConsistent,
                        topOnly,
                        geometryEvidence,
                        surfaceEvidence))
                .toList();
        CandidateStatus status = input.completeModelGeometry()
                && input.completeSurfaceEvidence()
                ? CandidateStatus.PREPARED
                : CandidateStatus.PARTIAL;
        evidence.add("RESOLVED_SURFACES:" + surfaces.size());
        return new StateInspection(
                surfaces,
                new InspectedCandidate(input.identity(), status, evidence),
                status == CandidateStatus.PREPARED ? List.of() : evidence);
    }

    /**
     * 中文：在 AUTO 推断前按精确面/精灵身份合并贡献者，完整面和显式 tint 优先。
     *
     * English: Merges contributors by exact face/sprite identity before AUTO inference, preferring
     * full faces and the first explicit tint.
     */
    public static List<FaceInput> aggregateExactContributors(List<FaceInput> contributors) {
        LinkedHashMap<FaceIdentity, FaceInput> aggregated = new LinkedHashMap<>();
        for (FaceInput contributor : Objects.requireNonNull(contributors, "contributors")) {
            FaceIdentity identity = new FaceIdentity(
                    contributor.face(),
                    contributor.source().spriteId());
            aggregated.merge(identity, contributor, FaceInput::merge);
        }
        return List.copyOf(aggregated.values());
    }

    private static InspectedSurface finish(
            String identity,
            FaceInput face,
            boolean spriteConsistent,
            boolean topOnly,
            FactState geometryEvidence,
            FactState surfaceEvidence) {
        InferenceFacts facts = new InferenceFacts(
                geometryEvidence,
                geometryEvidence.isTrue()
                        ? FactState.of(face.axisAligned())
                        : FactState.UNKNOWN,
                FactState.of(face.validUv()),
                surfaceEvidence.isTrue()
                        ? FactState.of(spriteConsistent)
                        : FactState.UNKNOWN,
                FactState.TRUE,
                FactState.of(face.source().opaque()),
                FactState.of(face.source().framedAlpha()),
                FactState.of(face.source().animated()),
                FactState.of(face.tintIndex() >= 0),
                geometryEvidence.isTrue()
                        ? FactState.of(face.fullBlock())
                        : FactState.UNKNOWN,
                geometryEvidence.isTrue()
                        ? FactState.of(!face.fullFace() || !face.fullBlock())
                        : FactState.UNKNOWN,
                surfaceEvidence.isTrue()
                        ? FactState.of(topOnly)
                        : FactState.UNKNOWN,
                FactState.FALSE,
                FactState.TRUE,
                EnumSet.of(ConnectionAxis.HORIZONTAL, ConnectionAxis.VERTICAL));
        return new InspectedSurface(
                identity,
                face.face(),
                face.source(),
                facts,
                face.fullFace(),
                face.tintIndex());
    }

    public record StateInput(
            String identity,
            String label,
            List<FaceInput> faces,
            boolean completeModelGeometry,
            boolean completeSurfaceEvidence,
            List<String> evidence) {
        public StateInput {
            requireText(identity, "state identity");
            requireText(label, "state label");
            faces = List.copyOf(Objects.requireNonNull(faces, "faces"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        }
    }

    public record FaceInput(
            SurfaceFace face,
            SurfaceSourceSnapshot source,
            boolean fullBlock,
            boolean axisAligned,
            boolean fullFace,
            boolean validUv,
            int tintIndex) {
        public FaceInput {
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(source, "source");
            if (tintIndex < -1) {
                throw new IllegalArgumentException("tintIndex must be -1 or non-negative");
            }
        }

        private FaceInput merge(FaceInput other) {
            FaceInput candidate = Objects.requireNonNull(other, "other");
            if (face != candidate.face
                    || !source.spriteId().equals(candidate.source.spriteId())) {
                throw new IllegalArgumentException(
                        "only identical face/sprite contributors may merge");
            }
            SurfaceRepresentativeFacts representative =
                    new SurfaceRepresentativeFacts(fullFace, tintIndex)
                            .merge(new SurfaceRepresentativeFacts(
                                    candidate.fullFace,
                                    candidate.tintIndex));
            return new FaceInput(
                    face,
                    source,
                    fullBlock || candidate.fullBlock,
                    axisAligned && candidate.axisAligned,
                    representative.fullFace(),
                    validUv && candidate.validUv,
                    representative.tintIndex());
        }
    }

    public record InspectedSurface(
            String stateIdentity,
            SurfaceFace face,
            SurfaceSourceSnapshot source,
            InferenceFacts inferenceFacts,
            boolean fullFace,
            int tintIndex) {
        public InspectedSurface {
            requireText(stateIdentity, "state identity");
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(inferenceFacts, "inferenceFacts");
        }
    }

    public record InspectedCandidate(
            String stateIdentity,
            CandidateStatus status,
            List<String> evidence) {
        public InspectedCandidate {
            requireText(stateIdentity, "state identity");
            Objects.requireNonNull(status, "status");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException("surface candidate must retain evidence");
            }
        }
    }

    public record StateInspection(
            List<InspectedSurface> surfaces,
            InspectedCandidate candidate,
            List<String> diagnostics) {
        public StateInspection {
            surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
            Objects.requireNonNull(candidate, "candidate");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }

        private static StateInspection unresolved(String identity, List<String> evidence) {
            return new StateInspection(
                    List.of(),
                    new InspectedCandidate(identity, CandidateStatus.UNRESOLVED, evidence),
                    evidence);
        }
    }

    public enum CandidateStatus {
        PREPARED,
        PARTIAL,
        UNRESOLVED
    }

    private record FaceIdentity(SurfaceFace face, String spriteId) {
        private FaceIdentity {
            Objects.requireNonNull(face, "face");
            requireText(spriteId, "sprite id");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
