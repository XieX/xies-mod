package xie.mods.xm.items;

import java.util.LinkedList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import java.util.List;

import xie.mods.xm.util.Drop;
import xie.mods.xm.util.Droppables;
import xie.mods.xm.util.Xie;

public class XieItemSword extends ItemSword implements XieItemInterface {
	
	protected int icon[];
	protected String names[];
	protected Droppables drops;
	
	protected int damage;
	protected int flame;
	protected int enchantability;
	protected List<Block> canCut;
	protected boolean canBlock;
	
	public XieItemSword(String name, int id, int index) {
		super(id-Xie.idShift, EnumToolMaterial.IRON);
        setIconIndex(index);
        setItemName(name);
        
		canCut = new LinkedList<Block>();
		canCut.add(Block.web);
		
		canBlock = true;
        maxStackSize = 1;
        setMaxDamage(250);
        damage = 6;
        enchantability = 14;
        flame = 0;
	}
	
	public void setWeaponDamage(int d) {
		damage = d;
	}
	
	public void setCanBlock(boolean b) {
		canBlock = b;
	}
	
	public void setFlame(int i) {
		flame = i;
	}
	
	public void setNumberOfUses(int i) {
		setMaxDamage(i);
	}

	public void setEnchantability(int e) {
		enchantability = e;
	}
	
	public void setCanCut(Block[] arr) {
		canCut = new LinkedList<Block>();
		for (Block b : arr) {
			canCut.add(b);
		}
	}
	
	// overrides (moslty copy-pasta from ItemSword.class)
    public float getStrVsBlock(ItemStack itemStack, Block block) {
        if (canCut.contains(block)) return 15F;
        else return 1.5F;
    }
	
    public boolean hitEntity(ItemStack itemStack, EntityLiving target, EntityLiving source) {
    	if (flame>0) target.setFire(flame);
        itemStack.damageItem(1, source);
        return true;
    }

    public int getDamageVsEntity(Entity par1Entity)
    {
        return damage;
    }

    public ItemStack onItemRightClick(ItemStack itemstack, World world, EntityPlayer player) {
    	if (canBlock) player.setItemInUse(itemstack, getMaxItemUseDuration(itemstack));
        return itemstack;
    }

    public boolean canHarvestBlock(Block block) {
    	if (canCut.contains(block)) return true;
        else return false;
    }

    public int getItemEnchantability()
    {
        return enchantability;
    }
    
    // XieItemInterface methods

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
