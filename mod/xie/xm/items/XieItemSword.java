package mod.xie.xm.items;

import java.util.LinkedList;

import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import java.util.List;


public class XieItemSword extends ItemSword implements XieItemInterface {
	protected String names[];
	protected Droppables drops;
	
	protected int damage;
	protected int flame;
	protected int enchantability;
	protected List<Block> canCut;
	protected boolean canBlock;
	
	public XieItemSword(String name, int id, EnumToolMaterial material) {
		super(id-Xie.idShift, material);
        setUnlocalizedName(name);
        
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
	
	protected XieTex icon;
	@Override
    public Icon getIconFromDamage(int i) {
		return icon.getIconFromDamage(i);
    }
	
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
	
	@Override
	public void registerIcons(IconRegister registrar) {
		icon = AutomagicTextureFactory.get(registrar, getUnlocalizedName().replace("item.", ""));
	} 
}
