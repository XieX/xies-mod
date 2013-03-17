package mod.xie.xm.util;

import java.util.LinkedList;
import java.util.List;


import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;


// Simple wrapper for items that contains an equals override
public class ItemWrapper implements Comparable {
	public int id;
	public int count;
	public int meta;
	
	public ItemWrapper(int id, int count, int meta) {
		if (id<0 || id>=Item.itemsList.length) id=-1;
		this.id=id;
		this.count=count;
		this.meta=meta;
	}
	
	public ItemWrapper(int id, int meta) {
		this(id,1,meta);
	}
	
	public ItemWrapper(int id) {
		this(id,-1);
	}
	
	public ItemWrapper(Item i) {
		this(i.itemID);
	}
	
	public ItemWrapper(Block b) {
		this(b.blockID);
	}
	
	public ItemWrapper(Object object) {
		meta=-1;
		count=1;
		
		if (object instanceof Block) {
			Block b = (Block)object;
			id=b.blockID;
		} else if (object instanceof Item) {
			Item i = (Item)object;
			id=i.itemID;
		} else if (object instanceof ItemStack) {
			ItemStack is = (ItemStack)object;
			id=is.itemID;
			count=is.stackSize;
			meta=is.getItemDamage();
		} else if (object instanceof Integer) {
			id = (Integer)object;
		} else throw new ClassCastException("Object cannot be identified as a Minecraft block or item");
	}
	
	public boolean exists() {
		if (id<0 || id>Item.itemsList.length) return false;
		return Item.itemsList[id]!=null;
	}

	public boolean hasMeta() {
		return meta>=0;
	}
	
	public ItemStack getItemStack() {
		if (meta<0) return new ItemStack(id,count,0);
		return new ItemStack(id,count,meta);
	}
	
	public ItemStack getItemStack(int amount) {
		if (meta<0) return new ItemStack(id,amount,0);
		return new ItemStack(id,amount,meta);
	}
	
	public boolean equals(ItemWrapper other) {
		if (hasMeta()) return id==other.id && meta==other.meta;
		else return id==other.id;
	}
	
	public boolean equals(ItemStack other) {
		if (hasMeta()) return id==other.itemID && meta==other.getItemDamage();
		else return id==other.itemID;
	}

	public String getUnlocalizedName() {
		if (exists()) return Item.itemsList[id].getUnlocalizedName();
		else return "null";
	}

	public Item getItem() {
		return Item.itemsList[id];
	}

	public Block getBlock() {
		if (isBlock()) return Block.blocksList[id];
		else return null;
	}
	
	public boolean isBlock() {
		return Item.itemsList[id] instanceof ItemBlock;
	}
	
	public boolean isItem() {
		return !isBlock();
	}
	
	@Override
	public String toString() {
		String s = getUnlocalizedName();
		if (hasMeta()) s+=":"+count+":"+meta;
		else if (count>1) s+="*"+count;
		return s;
	}
	
	public static List<ItemStack> toItemStackList(List<ItemWrapper> list) {
		List<ItemStack> isl = new LinkedList();
		for (ItemWrapper i : list) isl.add(i.getItemStack());
		return isl;
	}

	@Override
	public int compareTo(Object o) {
		ItemWrapper that = (ItemWrapper)o;				
		if (that.id<this.id) return -1;
		else if (that.id>this.id) return 1;
		
		if (!hasMeta()) return 0;
		else if (that.meta>this.meta) return 1;
		else if (that.meta<this.meta) return -1;
		
		return 0;
	}
}