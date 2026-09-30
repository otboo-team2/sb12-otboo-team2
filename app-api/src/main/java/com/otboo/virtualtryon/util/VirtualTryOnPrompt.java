package com.otboo.virtualtryon.util;

import com.otboo.clothes.entity.ClothesType;

public final class VirtualTryOnPrompt {

    private VirtualTryOnPrompt() {
    }

    public static String forType(ClothesType type) {
        String item = switch (type) {
            case TOP -> "top";
            case BOTTOM -> "bottoms (pants or skirt)";
            case DRESS -> "dress";
            case OUTER -> "outerwear";
            case ACCESSORY -> "accessory";
            case SHOES -> "shoes";
            case HAT -> "hat";
            case BAG -> "bag";
            case ETC -> "main item";
        };
        return ("Apply only the %s from the product image. "
            + "Ignore everything else shown in the product image worn by the product model. "
            + "Keep the rest of the person's outfit and accessories unchanged.")
            .formatted(item);
    }
}
