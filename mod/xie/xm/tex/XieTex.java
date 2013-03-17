package mod.xie.xm.tex;

import net.minecraft.util.Icon;

public interface XieTex {
	public Icon getTexFromSideAndMeta(int i, int j);
	public Icon getIconFromDamage(int par1);
	public XieTexType getType();
}
