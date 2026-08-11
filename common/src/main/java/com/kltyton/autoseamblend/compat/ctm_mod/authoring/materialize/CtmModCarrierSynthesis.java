package com.kltyton.autoseamblend.compat.ctm_mod.authoring.materialize;

import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot.SheetTile;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout.CarrierSpec;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModCarrierRecipePlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.ArrayList;
import java.util.Optional;

/** 中文：确定性生成 CTM Lib 物理载体单元。 / English: Deterministic materialization of CTM Lib's physical carrier cells. */
public final class CtmModCarrierSynthesis {
    private CtmModCarrierSynthesis() {}

    public static TextureSourceSnapshot create(
            TextureSourceSnapshot source,
            String outputTextureId,
            ConnectionMethod method,
            CarrierSpec spec,
            OverlayCutoutProfile overlayProfile) {
        ArrayList<Optional<SheetTile>> cells =
                new ArrayList<>(spec.cells().size());
        for (Optional<GeneratedTileRecipe> recipe :
                CtmModCarrierRecipePlan.recipes(method, spec)) {
            cells.add(recipe.map(value -> new SheetTile(
                    source,
                    value,
                    overlayProfile)));
        }
        TextureSourceSnapshot carrier = source
                .compositeSheetTo(
                        outputTextureId,
                        spec.columns(),
                        spec.rows(),
                        cells);
        return carrier.withSourceMetadata(CtmModCarrierRecipePlan.animationMetadata(
                source.sourceMetadata(),
                carrier.frameWidth(),
                carrier.frameHeight(),
                carrier.animated()));
    }
}
