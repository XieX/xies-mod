package mod.xie.xm.items;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import net.minecraft.item.Item;

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