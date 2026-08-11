package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * 中文：从创作预览虚拟世界的实际 connectBlocks 邻居解析覆盖层供体。
 *
 * English:
 * Resolves overlay donors from actual connectBlocks neighbors in the authoring
 * preview's virtual world.
 */
public final class PreviewConnectionDonors {
    private PreviewConnectionDonors() {}

    /**
     * 中文：只收集面平面内的直接和对角邻居，绝不把中心接收者作为供体。
     *
     * English:
     * Collects only direct and diagonal face-plane neighbors and never uses the
     * center receiver as a donor.
     */
    public static List<Donor> resolve(
            PreviewQuery query,
            Collection<Direction> directions) {
        return DocumentConnectionDonors.resolve(query, directions)
                .stream()
                .map(donor -> new Donor(
                        donor.state(),
                        donor.surface(),
                        donor.method()))
                .toList();
    }
}
