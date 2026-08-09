package com.kltyton.autoseamblend.authoring.selector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：无副作用、无损的原生选择器字段，保持缺省、空值与条目顺序。
 *
 * English: Side-effect-free lossless native selector field preserving absence,
 * empty values, and entry order.
 */
public final class NativeBlockSelectorField {
    private final boolean present;
    private final List<NativeBlockSelectorEntry> entries;
    private NativeBlockSelectorField(boolean present, List<NativeBlockSelectorEntry> entries) { this.present = present; this.entries = List.copyOf(Objects.requireNonNull(entries, "entries")); }
    public static NativeBlockSelectorField parse(boolean present, String raw, NativeBlockSelectorResolver resolver) {
        if (!present) return new NativeBlockSelectorField(false, List.of());
        if (raw == null || raw.isBlank()) return new NativeBlockSelectorField(true, List.of());
        ArrayList<NativeBlockSelectorEntry> parsed = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) if (!token.isBlank()) parsed.add(NativeBlockSelectorEntry.parse(token, resolver));
        return new NativeBlockSelectorField(true, parsed);
    }
    public static NativeBlockSelectorField registeredBlocks(boolean present, List<String> blockIds, NativeBlockSelectorResolver resolver) { ArrayList<NativeBlockSelectorEntry> parsed = new ArrayList<>(); for (String blockId : Objects.requireNonNull(blockIds, "blockIds")) parsed.add(NativeBlockSelectorEntry.forBlock(blockId, resolver)); return new NativeBlockSelectorField(present, parsed); }
    public boolean present() { return present; }
    public List<NativeBlockSelectorEntry> entries() { return entries; }
    public Presence presence() { if (!present) return Presence.ABSENT; if (entries.isEmpty()) return Presence.PRESENT_EMPTY; return entries.stream().allMatch(NativeBlockSelectorEntry::editable) ? Presence.PRESENT_VALUES : Presence.PRESENT_WITH_OPAQUE; }
    public List<String> blockIds() { return entries.stream().map(NativeBlockSelectorEntry::blockId).flatMap(Optional::stream).toList(); }
    public List<NativeBlockSelectorState> representativeStates(NativeBlockSelectorResolver resolver) { NativeBlockSelectorResolver validated = Objects.requireNonNull(resolver, "resolver"); return entries.stream().map(entry -> entry.blockId().flatMap(blockId -> validated.resolve(blockId)).flatMap(entry::representativeState)).flatMap(Optional::stream).toList(); }
    public Optional<String> firstDisplayValue() { return entries.stream().findFirst().map(NativeBlockSelectorEntry::serialized); }
    public Optional<String> firstBlockId() { return entries.stream().map(NativeBlockSelectorEntry::blockId).flatMap(Optional::stream).findFirst(); }
    public NativeBlockSelectorField addBlock(String blockId, NativeBlockSelectorResolver resolver) { NativeBlockSelectorEntry added = NativeBlockSelectorEntry.forBlock(blockId, resolver); if (entries.stream().anyMatch(entry -> entry.serialized().equals(added.serialized()))) return this; ArrayList<NativeBlockSelectorEntry> next = new ArrayList<>(entries); next.add(added); return new NativeBlockSelectorField(true, next); }
    public NativeBlockSelectorField remove(int index) { requireIndex(index); ArrayList<NativeBlockSelectorEntry> next = new ArrayList<>(entries); next.remove(index); return new NativeBlockSelectorField(true, next); }
    public NativeBlockSelectorField replace(int index, NativeBlockSelectorEntry entry) { requireIndex(index); ArrayList<NativeBlockSelectorEntry> next = new ArrayList<>(entries); next.set(index, Objects.requireNonNull(entry, "entry")); return new NativeBlockSelectorField(true, next); }
    public NativeBlockSelectorField move(int index, int delta) { requireIndex(index); int target = index + delta; if (target < 0 || target >= entries.size() || target == index) return this; ArrayList<NativeBlockSelectorEntry> next = new ArrayList<>(entries); NativeBlockSelectorEntry moved = next.remove(index); next.add(target, moved); return new NativeBlockSelectorField(true, next); }
    public Optional<String> serializedValue() { if (!present) return Optional.empty(); return Optional.of(entries.stream().map(NativeBlockSelectorEntry::serialized).reduce((left, right) -> left + ' ' + right).orElse("")); }
    public boolean matches(NativeBlockSelectorState state) { return entries.stream().anyMatch(entry -> entry.matches(state)); }
    private void requireIndex(int index) { if (index < 0 || index >= entries.size()) throw new IndexOutOfBoundsException(index); }
    public enum Presence { ABSENT, PRESENT_EMPTY, PRESENT_VALUES, PRESENT_WITH_OPAQUE }
}
