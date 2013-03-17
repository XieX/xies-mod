package mod.xie.xm.items;

import java.util.LinkedList;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import java.util.List;
import java.util.Set;


public class XieItemSet implements XieItemInterface {

	protected List<XieItemInterface> items;
	protected String name;
	
	public XieItemSet(String n) {
		name = n;
		items = new LinkedList<XieItemInterface>();
	}
	
	public void add(XieItemInterface item) {
		items.add(item);
	}
	
	@Override
	public Item getItem() {
		if (items.isEmpty()) return null;
		else return items.get(0).getItem();
	}

	@Override
	public void setDrops(Droppables drops) {
		if (items.isEmpty()) return;
		
		for (XieItemInterface i : items) {
			i.setDrops(drops);
		}
	}

	@Override
	public void addDrop(Drop drop) {
		if (items.isEmpty()) return;
		
		for (XieItemInterface i : items) {
			i.addDrop(drop);
		}
	}

	@Override
	public void setNames(String[] n) {
		// do nothing
	}

	@Override
	public void setIcons(int[] i) {
		// do nothing
	}

	@Override
	public Item setHasSubtypes(boolean b) {
		return this.getItem();
	}

	@Override
	public String getUnlocalizedName() {
		return name;
	}

	@Override
	public Droppables getDrops() {
		if (items.isEmpty()) return new Droppables();
		return items.get(0).getDrops();
	}

	@Override
	public String getTexFile() {
		if (items.isEmpty()) return "/gui/items.png";
		return items.get(0).getTexFile();
	}

	@Override
	public void setTexFile(String texFile) {
		if (items.isEmpty()) return;
		
		for (XieItemInterface i : items) {
			i.setTexFile(texFile);
		}
	}

	@Override
	public String getTextureFile() {
		return this.getTexFile();
	}

	@Override
	public Item setMaxStackSize(int n) {
		if (items.isEmpty()) return null;
		
		for (XieItemInterface i : items) {
			i.setMaxStackSize(n);
		}
		
		return items.get(0).getItem();
	}

}
