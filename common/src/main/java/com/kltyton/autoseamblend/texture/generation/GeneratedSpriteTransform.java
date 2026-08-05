package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.Objects;
import java.util.Optional;

/** 中文：一个内存状态精灵的值语义像素变换。 / English: Value-based pixel transform for one in-memory state sprite. */
public sealed interface GeneratedSpriteTransform
        permits GeneratedSpriteTransform.TileRecipe,
                GeneratedSpriteTransform.ProceduralPlan {
    int[] materialize(
            int width,
            int height,
            int[] sourceStraightArgb);

    record TileRecipe(
            GeneratedTileRecipe recipe,
            Optional<OverlayCutoutProfile> overlayProfile)
            implements GeneratedSpriteTransform {
        public TileRecipe(GeneratedTileRecipe recipe) {
            this(recipe, Optional.empty());
        }

        public TileRecipe(
                GeneratedTileRecipe recipe,
                OverlayCutoutProfile overlayProfile) {
            this(
                    recipe,
                    Optional.of(Objects.requireNonNull(
                            overlayProfile,
                            "overlayProfile")));
        }

        public TileRecipe {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(
                    overlayProfile,
                    "overlayProfile");
        }

        @Override
        public int[] materialize(
                int width,
                int height,
                int[] sourceStraightArgb) {
            return overlayProfile
                    .map(profile -> GeneratedTileMaterializer
                            .materializeStraightArgb(
                                    width,
                                    height,
                                    sourceStraightArgb,
                                    recipe,
                                    profile))
                    .orElseGet(() -> GeneratedTileMaterializer
                            .materializeStraightArgb(
                                    width,
                                    height,
                                    sourceStraightArgb,
                                    recipe));
        }
    }

    record ProceduralPlan(ProceduralConnectionPlan plan)
            implements GeneratedSpriteTransform {
        public ProceduralPlan {
            Objects.requireNonNull(plan, "plan");
        }

        @Override
        public int[] materialize(
                int width,
                int height,
                int[] sourceStraightArgb) {
            return ProceduralPlanMaterializer
                    .materializeStraightArgb(
                            width,
                            height,
                            sourceStraightArgb,
                            plan);
        }
    }
}
