package mod.xie.xm.items;

import java.util.HashMap;
import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumArmorMaterial;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ISpecialArmor;

public class XieItemArmor extends ItemArmor implements XieItemInterface, ISpecialArmor {
	
	public static final String SUFFIXES[] = new String[] {"helm", "chest", "legs", "boots"};
	
    protected String armorTexture;
    protected ArmorEffects properties;
	
    protected XieTex icon;
    
	public XieItemArmor(String name, int id, EnumArmorMaterial material, int type) {
		super(id-Xie.idShift, material, 0, type);
        maxStackSize = 1;
        armorTexture="/armor/cloth_1.png";

        setUnlocalizedName(name);
        
        properties = new ArmorEffects();
	}
	
	public void setArmorTextureFile(String str) {
		armorTexture=str;
	}
	
	public void setProperties(ArmorEffects props) {
		properties=props;
	}
	
	public String getArmorTextureFile(ItemStack itemstack) {
		return armorTexture;
	}
	
	@Override
	public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source, double damage, int slot) {
		int resistanceFactor = properties.getResistanceTo(source);
		
		if (resistanceFactor==0) {
			double dmgReduced = damageReduceAmount / 25D;
			int maxDamage = getMaxDamage() + 1 - armor.getItemDamage();
			return new ArmorProperties(0, dmgReduced, maxDamage);
		} else {
			double resistAmount = (damage*resistanceFactor)/100;
			double dmgReduced = (damageReduceAmount + resistAmount) / 25D;
			if (dmgReduced<0) dmgReduced = 0;
			int maxDamage = (int) ((getMaxDamage() + 1 - armor.getItemDamage()) + Math.ceil(resistAmount));
			if (maxDamage<0) maxDamage=0;
			return new ArmorProperties(0, dmgReduced, maxDamage);
		}
	}

	@Override
	public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
		// TODO incorporate special properties
		return damageReduceAmount;
	}

	@Override
	public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) {
		// TODO incorporate special properties
		stack.damageItem(damage, entity);
	}
	
	// XieItemInterface fields and methods
	protected String names[];
	protected Droppables drops;
	
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
	public void setNames(String n[]) { 
		names=n;
		if (n.length>0) {
			this.setHasSubtypes(true);
		}
	}
	
	@Override
    public Icon getIconFromDamage(int i) {
    	return icon.getIconFromDamage(i);
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
		icon = AutomagicTextureFactory.getItem(registrar, getUnlocalizedName().replace("item.", ""));
	}
}
