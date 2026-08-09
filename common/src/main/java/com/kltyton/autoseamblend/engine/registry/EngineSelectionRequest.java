package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import java.util.List;
import java.util.Optional;

public record EngineSelectionRequest(
        Optional<DocumentClaim> documentClaim,
        List<NativeOwnership> ownershipClaims) {
    public EngineSelectionRequest {
        documentClaim = documentClaim == null ? Optional.empty() : documentClaim;
        ownershipClaims = List.copyOf(ownershipClaims);
    }

    public static EngineSelectionRequest automatic() {
        return new EngineSelectionRequest(Optional.empty(), List.of());
    }

    public record DocumentClaim(
            String engineId,
            SourceTier tier,
            AutoBlendPolicy autoBlendPolicy,
            int packPriority) {
        public DocumentClaim {
            if (engineId == null || engineId.isBlank()) {
                throw new IllegalArgumentException("engineId must not be blank");
            }
            if (tier == null) throw new IllegalArgumentException("tier must not be null");
            if (autoBlendPolicy == null) throw new IllegalArgumentException("autoBlendPolicy must not be null");
        }

        public int sourcePriority() {
            return tier.priority();
        }
    }
}
