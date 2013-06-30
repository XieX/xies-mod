package mod.xie.xm.items;

import java.util.LinkedList;

import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import java.util.List;


public class XieItemBow extends ItemBow implements XieItemInterface {

	protected List<Item> ammunition;
	
	protected int baseFlame;
	protected int basePower;
	protected int basePunch;
	
	protected boolean ammoless;
	protected boolean semiautomatic;
	protected boolean ammoConsumed;
	
	protected int enchantability;
	
	public XieItemBow(String name, int id) {
		super(id - Xie.idShift);
        setUnlocalizedName(name);
        
        ammunition = new LinkedList<Item>();
        ammunition.add(Item.arrow);
        
        baseFlame = 0;
        basePower = 0;
        basePunch = 0;
        ammoless = false;
        semiautomatic = false;
        enchantability = 1;
        ammoConsumed = false;
	}
	
	// setters
	
	public void setNumberOfUses(int i) {
		setMaxDamage(i);
	}
	
	public void setAmmunition(List<ItemWrapper> l) {
		ammunition = new LinkedList<Item>();
		if (l.isEmpty()) return;
		for (ItemWrapper i : l) ammunition.add(i.getItem());
	}
	
	public void setAmmoConsumed(boolean b) {
		ammoConsumed = b;
	}
	
	public void setFlame(int i) {
		baseFlame = i;
	}
	
	public void setPower(int i) {
		basePower = i;
	}
	
	public void setPunch(int i) {
		basePunch = i;
	}
	
	public void setSemiautomatic(boolean b) {
		semiautomatic = b;
	}
	
	public void setAmmoless(boolean b) {
		ammoless = b;
	}
	
	public void setEnchantability(int i) {
		enchantability = i;
	}
	
	// new methods
	protected boolean hasAmmo(EntityPlayer player) {
		if (ammoless) return true;
		if (ammunition.isEmpty()) return false;
		
		for (Item i : ammunition) {
			if (player.inventory.hasItem(i.itemID)) return true;
		}
		
		return false;
	}
	
	protected Item getAmmo(EntityPlayer player) {
		if (ammunition.isEmpty()) return Item.arrow;
		
		for (Item i : ammunition) {
			if (player.inventory.hasItem(i.itemID)) return i;
		}
		
		return ammunition.get(0);
	}
	
	// overrides (mostly copy-pasta from ItemBow.class)
	
	public void onPlayerStoppedUsing(ItemStack itemstack, World world, EntityPlayer player, int count) {
		if (semiautomatic) return;
		else fire(itemstack,world,player,count);
	}
		
	protected void fire(ItemStack itemstack, World world, EntityPlayer player, int count) {
        boolean freeFire = ammoless 
        		|| player.capabilities.isCreativeMode 
        		|| EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, itemstack) > 0;

        if (freeFire || hasAmmo(player)) {
        	Item ammo = getAmmo(player);
        	
        	float force;
        	if (semiautomatic) {
        		force = world.rand.nextFloat();
        		if (force < 0.9F) force = 0.9F;
        	} else {
        		int i = getMaxItemUseDuration(itemstack) - count;
        		force = i / 20F;
            	force = (force * force + force * 2.0F) / 3F;
            	if (force < 0.10000000000000001D) return;
            	if (force > 1.0F) force = 1.0F;
        	}
            
            XieEntityArrow entityarrow = new XieEntityArrow(world, player, force * 2.0F, ammo);

            if (force == 1.0F) entityarrow.setIsCritical(true);

            int power = basePower + EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemstack);
            if (power > 0) entityarrow.setDamage(entityarrow.getDamage() + power * 0.5D + 0.5D);

            int punch = basePunch + EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, itemstack);
            if (punch > 0) entityarrow.setKnockbackStrength(punch);

            int flame = baseFlame + EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, itemstack) > 0 ? 100 : 0; 
            if (flame > 0) entityarrow.setFire(flame);

            itemstack.damageItem(1, player);
            world.playSoundAtEntity(player, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + force * 0.5F);

            if (!freeFire) {
                player.inventory.consumeInventoryItem(ammo.itemID);
                if (ammoConsumed) entityarrow.canBePickedUp = 0;
            } else {
                entityarrow.canBePickedUp = 0;
            }

            if (!world.isRemote) {
                world.spawnEntityInWorld(entityarrow);
            }
        }
    }
	
    public ItemStack onItemRightClick(ItemStack itemStack, World world, EntityPlayer player) {
    	if (player.capabilities.isCreativeMode || hasAmmo(player)) {
    		if (semiautomatic) {
    			fire(itemStack,world,player,1);
    		} else player.setItemInUse(itemStack, getMaxItemUseDuration(itemStack));
        }

        return itemStack;
    }

    public int getItemEnchantability() {
        return enchantability;
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
		icon = AutomagicTextureFactory.getItem(registrar, getUnlocalizedName().replace("item.", ""));
	} 
}
