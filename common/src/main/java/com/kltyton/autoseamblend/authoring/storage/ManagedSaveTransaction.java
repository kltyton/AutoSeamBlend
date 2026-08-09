package com.kltyton.autoseamblend.authoring.storage;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.property.NativePropertyPatch;
import com.kltyton.autoseamblend.authoring.property.NativePropertyPatchApplier;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：统一显式保存的准备、提交、一次重载门禁和回滚状态机。
 *
 * English: Shared explicit-save state machine for preparation, commit, the
 * one-reload gate, and rollback.
 *
 * <p>The Loader supplies only the frozen workspace layout. Pack repository
 * ordering and the actual reload future stay in the Loader activation port;
 * this type makes it impossible for either Loader to skip the common file
 * transaction phases or request the reload gate twice.</p>
 */
public final class ManagedSaveTransaction {
    private final ManagedPackWriteLayout layout;
    private final LinkedHashMap<String, byte[]> files;
    private Phase phase = Phase.PREPARED;
    private ManagedPackTransaction.CommitResult committed;
    private CommitSummary summary;

    private ManagedSaveTransaction(
            ManagedPackWriteLayout layout,
            LinkedHashMap<String, byte[]> files) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.files = new LinkedHashMap<>();
        files.forEach((path, content) -> this.files.put(path, content.clone()));
    }

    /**
     * 中文：按项目、属性补丁、编辑文件的固定顺序准备一次保存。
     *
     * English: Prepare one save in the fixed order of projects, property
     * patches, then edited files.
     */
    public static ManagedSaveTransaction prepare(
            ManagedPackWriteLayout layout,
            List<ManagedAuthoringProject> projects,
            Map<String, byte[]> editedFiles,
            List<NativePropertyPatch> propertyPatches)
            throws IOException {
        return prepare(
                layout,
                projects,
                editedFiles,
                propertyPatches,
                NativeDocumentOperations.shared());
    }

    public static ManagedSaveTransaction prepare(
            ManagedPackWriteLayout layout,
            List<ManagedAuthoringProject> projects,
            Map<String, byte[]> editedFiles,
            List<NativePropertyPatch> propertyPatches,
            NativeDocumentOperations operations)
            throws IOException {
        ManagedPackWriteLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        LinkedHashMap<String, byte[]> transaction = new LinkedHashMap<>();
        for (ManagedAuthoringProject project : List.copyOf(
                Objects.requireNonNull(projects, "projects"))) {
            ManagedNativeDocumentMerge.mergeInto(
                    checkedLayout,
                    Objects.requireNonNull(project, "project"),
                    transaction,
                    operations);
        }
        NativePropertyPatchApplier.apply(
                transaction,
                List.copyOf(Objects.requireNonNull(propertyPatches, "propertyPatches")),
                operations);
        for (Map.Entry<String, byte[]> entry : Objects.requireNonNull(
                editedFiles,
                "editedFiles").entrySet()) {
            String path = ManagedPathPolicy.validateRelative(
                    Objects.requireNonNull(entry.getKey(), "edited path"));
            byte[] content = Objects.requireNonNull(
                    entry.getValue(),
                    "edited content").clone();
            if (transaction.putIfAbsent(path, content) != null) {
                throw new IllegalArgumentException(
                        "EDITED_FILE_COLLIDES_WITH_NATIVE_DOCUMENT:" + path);
            }
        }
        ManagedPathPolicy.rejectCaseCollisions(transaction.keySet());
        return new ManagedSaveTransaction(checkedLayout, transaction);
    }

    /**
     * 中文：把已经准备好的文件提交到同级临时文件事务。
     *
     * English: Commit the prepared files through the sibling-temporary file
     * transaction.
     */
    public synchronized CommitSummary commit() throws IOException {
        requirePhase(Phase.PREPARED, "commit");
        committed = ManagedPackTransaction.commit(
                layout,
                files,
                ManagedPackMetadata.defaultPackMetadata());
        summary = new CommitSummary(
                committed.workspaceCreated(),
                committed.changedPaths());
        phase = Phase.COMMITTED;
        return summary;
    }

    /**
     * 中文：标记唯一一次客户端重载即将发生；调用方必须在 reload future 前调用。
     *
     * English: Mark the single client reload as requested; the Loader must call
     * this immediately before obtaining its reload future.
     */
    public synchronized void beginReload() {
        requirePhase(Phase.COMMITTED, "begin reload");
        phase = Phase.RELOAD_REQUESTED;
    }

    /**
     * 中文：成功重载后删除回滚备份。
     *
     * English: Discard rollback backups after a successful reload.
     */
    public synchronized void finish() throws IOException {
        if (phase == Phase.FINISHED) {
            return;
        }
        requireOneOf(Phase.COMMITTED, Phase.RELOAD_REQUESTED, "finish");
        committed.finish();
        phase = Phase.FINISHED;
    }

    /**
     * 中文：在提交失败、代次失效或重载失败时恢复所有文件。
     *
     * English: Restore all files after a failed commit, stale generation, or
     * reload failure.
     */
    public synchronized void rollback() throws IOException {
        if (phase == Phase.ROLLED_BACK || phase == Phase.PREPARED) {
            phase = Phase.ROLLED_BACK;
            return;
        }
        if (phase == Phase.FINISHED) {
            return;
        }
        requireOneOf(Phase.COMMITTED, Phase.RELOAD_REQUESTED, "rollback");
        committed.rollback();
        phase = Phase.ROLLED_BACK;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized CommitSummary summary() {
        if (summary == null) {
            throw new IllegalStateException("save has not been committed");
        }
        return summary;
    }

    private void requirePhase(Phase expected, String operation) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "cannot " + operation + " while save is " + phase);
        }
    }

    private void requireOneOf(Phase first, Phase second, String operation) {
        if (phase != first && phase != second) {
            throw new IllegalStateException(
                    "cannot " + operation + " while save is " + phase);
        }
    }

    public enum Phase {
        PREPARED,
        COMMITTED,
        RELOAD_REQUESTED,
        FINISHED,
        ROLLED_BACK
    }

    public record CommitSummary(boolean workspaceCreated, List<String> changedPaths) {
        public CommitSummary {
            changedPaths = List.copyOf(
                    Objects.requireNonNull(changedPaths, "changedPaths"));
        }
    }
}
