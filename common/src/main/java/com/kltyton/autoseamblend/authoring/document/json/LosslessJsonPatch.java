package com.kltyton.autoseamblend.authoring.document.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中文：提供原生 authoring JSON 的确定性、无 I/O 局部字段补丁。
 * English: Provides deterministic, I/O-free field patches for native authoring JSON.
 */
public final class LosslessJsonPatch {
    private LosslessJsonPatch() {}

    public static String replaceRootKeys(
            String existing,
            String existingError,
            String desired,
            String desiredError,
            String... keys) throws IOException {
        JsonSourceDocument desiredDocument = JsonSourceDocument.parse(
                desired, desiredError);
        return replaceKeys(
                existing,
                existingError,
                List.of(),
                desiredDocument,
                desiredDocument.root(),
                keys);
    }

    public static String replaceNestedKeys(
            String existing,
            String existingError,
            String desired,
            String desiredError,
            String containerKey,
            boolean removeWhenEmpty,
            String... keys) throws IOException {
        JsonSourceDocument desiredDocument = JsonSourceDocument.parse(
                desired, desiredError);
        JsonSourceDocument.ObjectSpan desiredContainer = desiredDocument.objectAt(
                desiredDocument.root(), containerKey);
        if (desiredContainer == null) {
            desiredContainer = JsonSourceDocument.ObjectSpan.empty();
        }

        JsonSourceDocument existingDocument = JsonSourceDocument.parse(
                existing, existingError);
        JsonSourceDocument.MemberSpan existingContainer = existingDocument.root()
                .last(containerKey);
        String patched;
        if (existingContainer != null
                && existingContainer.value() instanceof JsonSourceDocument.ObjectSpan) {
            patched = replaceKeys(
                    existing,
                    existingError,
                    List.of(containerKey),
                    desiredDocument,
                    desiredContainer,
                    keys);
        } else {
            patched = replaceKeys(
                    "{}",
                    existingError,
                    List.of(),
                    desiredDocument,
                    desiredContainer,
                    keys);
            JsonSourceDocument replacement = JsonSourceDocument.parse(
                    patched, existingError);
            if (removeWhenEmpty && replacement.root().members().isEmpty()) {
                return existingContainer == null
                        ? existing
                        : JsonObjectPatchEditor.removeMember(
                                existingDocument, existingDocument.root(), containerKey);
            }
            patched = JsonObjectPatchEditor.setMember(
                    existingDocument, existingDocument.root(), containerKey, patched);
        }

        JsonSourceDocument result = JsonSourceDocument.parse(patched, existingError);
        JsonSourceDocument.ObjectSpan resultContainer = result.objectAt(
                result.root(), containerKey);
        if (removeWhenEmpty
                && resultContainer != null
                && resultContainer.members().isEmpty()) {
            return JsonObjectPatchEditor.removeMember(
                    result, result.root(), containerKey);
        }
        return patched;
    }

    public static String fillMissing(
            String existing,
            String existingError,
            String desired,
            String desiredError) throws IOException {
        JsonSourceDocument desiredDocument = JsonSourceDocument.parse(
                desired, desiredError);
        return fillMissing(
                existing,
                existingError,
                List.of(),
                desiredDocument,
                desiredDocument.root());
    }

    public static String patchRootValues(
            String existing,
            String existingError,
            Map<String, Optional<String>> values,
            String valueError) throws IOException {
        JsonSourceDocument.parse(existing, existingError);
        JsonObjectPatchEditor.validateValues(values, valueError);
        return JsonObjectPatchEditor.patchValues(
                existing, existingError, List.of(), values);
    }

    public static String patchNestedValues(
            String existing,
            String existingError,
            String containerKey,
            boolean removeWhenEmpty,
            Map<String, Optional<String>> values,
            String valueError) throws IOException {
        JsonSourceDocument existingDocument = JsonSourceDocument.parse(
                existing, existingError);
        JsonObjectPatchEditor.validateValues(values, valueError);
        JsonSourceDocument.MemberSpan container = existingDocument.root()
                .last(containerKey);
        String patched;
        if (container != null
                && container.value() instanceof JsonSourceDocument.ObjectSpan) {
            patched = JsonObjectPatchEditor.patchValues(
                    existing, existingError, List.of(containerKey), values);
        } else {
            String replacement = JsonObjectPatchEditor.patchValues(
                    "{}", existingError, List.of(), values);
            JsonSourceDocument replacementDocument = JsonSourceDocument.parse(
                    replacement, existingError);
            if (removeWhenEmpty
                    && replacementDocument.root().members().isEmpty()) {
                return container == null
                        ? existing
                        : JsonObjectPatchEditor.removeMember(
                                existingDocument,
                                existingDocument.root(),
                                containerKey);
            }
            patched = JsonObjectPatchEditor.setMember(
                    existingDocument,
                    existingDocument.root(),
                    containerKey,
                    replacement);
        }
        JsonSourceDocument result = JsonSourceDocument.parse(patched, existingError);
        JsonSourceDocument.ObjectSpan resultContainer = result.objectAt(
                result.root(), containerKey);
        if (removeWhenEmpty
                && resultContainer != null
                && resultContainer.members().isEmpty()) {
            return JsonObjectPatchEditor.removeMember(
                    result, result.root(), containerKey);
        }
        return patched;
    }

    private static String replaceKeys(
            String existing,
            String existingError,
            List<String> targetPath,
            JsonSourceDocument desiredDocument,
            JsonSourceDocument.ObjectSpan desiredObject,
            String... keys) throws IOException {
        String patched = existing;
        for (String key : keys) {
            JsonSourceDocument targetDocument = JsonSourceDocument.parse(
                    patched, existingError);
            JsonSourceDocument.ObjectSpan targetObject = targetDocument.objectAt(
                    targetDocument.root(), targetPath);
            if (targetObject == null) {
                throw new IOException(existingError);
            }
            JsonSourceDocument.MemberSpan desiredMember = desiredObject.last(key);
            patched = desiredMember == null
                    ? JsonObjectPatchEditor.removeMember(
                            targetDocument, targetObject, key)
                    : JsonObjectPatchEditor.setMember(
                            targetDocument,
                            targetObject,
                            key,
                            desiredDocument.raw(desiredMember.value()));
        }
        return patched;
    }

    private static String fillMissing(
            String existing,
            String existingError,
            List<String> targetPath,
            JsonSourceDocument desiredDocument,
            JsonSourceDocument.ObjectSpan desiredObject) throws IOException {
        String patched = existing;
        for (JsonSourceDocument.MemberSpan desiredMember
                : desiredObject.effectiveMembers()) {
            JsonSourceDocument targetDocument = JsonSourceDocument.parse(
                    patched, existingError);
            JsonSourceDocument.ObjectSpan targetObject = targetDocument.objectAt(
                    targetDocument.root(), targetPath);
            if (targetObject == null) {
                throw new IOException(existingError);
            }
            JsonSourceDocument.MemberSpan targetMember = targetObject.last(
                    desiredMember.name());
            if (targetMember == null) {
                patched = JsonObjectPatchEditor.setMember(
                        targetDocument,
                        targetObject,
                        desiredMember.name(),
                        desiredDocument.raw(desiredMember.value()));
                continue;
            }
            if (targetMember.value() instanceof JsonSourceDocument.ObjectSpan
                    && desiredMember.value()
                            instanceof JsonSourceDocument.ObjectSpan desiredChild) {
                ArrayList<String> childPath = new ArrayList<>(targetPath);
                childPath.add(desiredMember.name());
                patched = fillMissing(
                        patched,
                        existingError,
                        List.copyOf(childPath),
                        desiredDocument,
                        desiredChild);
            }
        }
        return patched;
    }

}
