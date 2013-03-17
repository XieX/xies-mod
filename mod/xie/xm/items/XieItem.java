package mod.xie.xm.items;

import java.util.LinkedList;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;


// Referenced classes of package net.minecraft.src:
//            Item, World, Block, ItemStack, 
//            EntityPlayer

public class XieItem extends Item implements XieItemInterface {
	protected int icon[];
	protected String names[];
	protected Droppables drops;

    public XieItem(String name, int i, int j)
    {
        super(i-Xie.idShift);
        setIconIndex(j);
        setUnlocalizedName(name);
        this.setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemstack, World world, EntityPlayer entityplayer) {
        // check that item is usable in some fashion
        if (drops==null || drops.isEmpty()) return itemstack;
        
        LinkedList<ItemStack> drop = new LinkedList(drops.getDrops());
        if (drop==null || drop.isEmpty()) return itemstack;
        
        if (itemstack.stackSize==1) {
        	itemstack = drop.pop();
        	itemstack = new ItemStack(itemstack.itemID,itemstack.stackSize,itemstack.getItemDamage());
        } else itemstack.stackSize--;
      
        Xie.giveItemsToPlayer(world, entityplayer, drop);
    	
    	return itemstack;
    }

	@Override
	public void setDrops(Droppables dropz) {
		drops=dropz;
	}
	
	@Override
	public void addDrop(Drop drop) {
		if (drops==null) drops=new Droppables(drop);
		else drops.add(drop);
	}
	
	@Override
	public void setNames(String n[]) { names=n; }
	
    public int getIconFromDamage(int i)
    {
    	if (icon==null) return iconIndex;
    	
        if (icon.length>1 && i<icon.length) {
        	return icon[i];
        } else return icon[0];
    }
    
    @Override
	public void setIcons(int i[]) { icon=i; }
	
	@Override
	public Item setHasSubtypes(boolean b) {
		return super.setHasSubtypes(b);
	}
	
	public String getSubName(int damage) {
		if (names==null) return super.getUnlocalizedName();
		if (damage>=names.length) return super.getUnlocalizedName();
		return names[damage];
	}
	
	public String getItemNameIS(ItemStack itemstack) {
		if (names==null) return super.getUnlocalizedName();
		else return (new StringBuilder()).append(super.getUnlocalizedName()).append(".").append(getSubName(itemstack.getItemDamage())).toString();
	}
	
	@Override
	public Item getItem() {
		return this;
	}
	
	@Override
	public Droppables getDrops() { return drops; }

	public String texFile;
	@Override
	public String getTexFile() {if (texFile==null) return null; else return texFile;}
	@Override
	public void setTexFile(String texFile) {this.texFile=texFile;}
	@Override
	public String getTextureFile() {
		return Xie.textureWrapper(this);
	} 
}
