package cn.ssjjcompat;

import com.tacz.guns.client.model.BedrockAnimatedModel;

/** TaCZ builds root traversal from a map, so JSON bone order is not a contract. */
public final class SkinTailOrder {
    private SkinTailOrder() {}
    public static void ensure(BedrockAnimatedModel model) {
        if (model == null) return;
        var tail = model.getNode("skin_fx_end");
        var roots = model.getShouldRender();
        if (tail == null) {
            model.setFunctionalRenderer("skin_fx_end", part -> null);
            tail = model.getNode("skin_fx_end");
            roots.add(tail);
        }
        if (tail.getParent() != null || !tail.cubes.isEmpty() || !tail.children.isEmpty()) return;
        if (!roots.contains(tail)) return;
        if (roots.get(roots.size()-1) != tail) {
            roots.remove(tail);
            roots.add(tail);
        }
    }
}
