package com.arcaneforge.client.render;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.item.ModularWeaponItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** GeckoLib item renderer for the flagship weapons, with an emissive rune/glow layer. */
@Environment(EnvType.CLIENT)
public class ArcaneWeaponRenderer extends GeoItemRenderer<ModularWeaponItem> {
    public ArcaneWeaponRenderer(String modelName) {
        super(new DefaultedItemGeoModel<>(ArcaneForge.id(modelName)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
