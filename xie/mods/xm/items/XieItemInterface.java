package xie.mods.xm.items;

import net.minecraft.item.Item;
import xie.mods.xm.util.Drop;
import xie.mods.xm.util.Droppables;

public interface XieItemInterface {
	Item getItem();
	void setDrops(Droppables drops);
	void addDrop(Drop drop);
	void setNames(String n[]);
	void setIcons(int i[]);
	public Item setHasSubtypes(boolean b);
    String getItemName();
	Droppables getDrops();
	String getTexFile();
	void setTexFile(String texFile);
	public String getTextureFile();
	Item setMaxStackSize(int i);
}