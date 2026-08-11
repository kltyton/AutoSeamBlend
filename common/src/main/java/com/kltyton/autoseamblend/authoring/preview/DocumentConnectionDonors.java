package com.kltyton.autoseamblend.authoring.preview;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：从文档虚拟世界解析直接与对角连接供体。 / English: Resolves direct and diagonal connection donors from a document virtual world. */
public final class DocumentConnectionDonors {
    private DocumentConnectionDonors() {}

    public static List<PreviewConnectionDonor> resolve(
            PreviewQuery query,
            Collection<Direction> directions) {
        PreviewQuery checkedQuery = Objects.requireNonNull(query, "query");
        List<Direction> directionList = List.copyOf(
                Objects.requireNonNull(directions, "directions"));
        LinkedHashSet<BlockState> states = new LinkedHashSet<>();
        directionList.forEach(direction -> states.add(
                checkedQuery.level().getBlockState(
                        checkedQuery.pos().relative(direction))));
        for (int first = 0; first < directionList.size(); first++) {
            for (int second = first + 1; second < directionList.size(); second++) {
                Direction firstDirection = directionList.get(first);
                Direction secondDirection = directionList.get(second);
                if (firstDirection.getAxis() != secondDirection.getAxis()) {
                    states.add(checkedQuery.level().getBlockState(
                            checkedQuery.pos()
                                    .relative(firstDirection)
                                    .relative(secondDirection)));
                }
            }
        }
        return states.stream()
                .filter(state -> checkedQuery.connects(checkedQuery.state(), state))
                .map(state -> checkedQuery.surfaces()
                        .preferredFace(state, checkedQuery.face())
                        .map(surface -> new PreviewConnectionDonor(
                                state,
                                surface,
                                checkedQuery.resolvedMethod())))
                .flatMap(Optional::stream)
                .toList();
    }
}
