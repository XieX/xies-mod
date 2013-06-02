package mod.xie.xm.tex;

import mod.xie.xm.util.ItemWrapper;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.Icon;

public class XieTexLink extends XieTex {

	protected Block block;
	protected Item item;
	
	protected XieTexLink(IconRegister registrar, Block link) {
		super(registrar);
		block = link;
	}
	protected XieTexLink(IconRegister registrar, Item link) {
		super(registrar);
		item = link;
	}

	@Override
	public Icon getIcon(int i, int j) {
		if (block != null) {
			return block.getIcon(i, j);
		} else if (item != null) {
			return item.getIconFromDamage(j);
		}
		
		return null;
	}

	@Override
	public Icon getIconFromDamage(int j) {
		if (block != null) {
			return block.getIcon(2, j);
		} else if (item != null) {
			return item.getIconFromDamage(j);
		}
		
		return null;
	}

	@Override
	public XieTexType getType() {
		return XieTexType.LINK;
	}

}
