package xie.mods.xm.items;

import xie.mods.xm.util.Drop;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import xie.mods.xm.util.Droppables;
import xie.mods.xm.util.Xie;


public class XieItemPotion extends ItemPotion implements XieItemInterface {
	protected int icon[];
	protected String names[];
	protected Droppables drops;
	
    public XieItemPotion(String name, int i, int j) {
        super(i-Xie.idShift);
        setIconIndex(j);
        setItemName(name);
    }
	
	// copy-pasta from XieItem
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
		if (names==null) return super.getItemName();
		if (damage>=names.length) return super.getItemName();
		return names[damage];
	}
	
	public String getItemNameIS(ItemStack itemstack) {
		if (names==null) return super.getItemName();
		else return (new StringBuilder()).append(super.getItemName()).append(".").append(getSubName(itemstack.getItemDamage())).toString();
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
