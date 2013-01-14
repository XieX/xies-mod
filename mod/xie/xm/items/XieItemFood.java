package mod.xie.xm.items;

import java.util.HashMap;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.PotionEffectWrapper;
import mod.xie.xm.util.Thirsty;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


// Referenced classes of package net.minecraft.src:
//            Item, World, Block, ItemStack, 
//            EntityPlayer

// TODO serious work on fixing the potential for multiple items with the same damage
public class XieItemFood extends ItemFood implements XieItemInterface {
	protected int icon[];
	protected Droppables drops;
	protected String names[];
	protected EnumAction useAction;
	protected Map<Integer,List<PotionEffectWrapper>> effects;
	
	protected int thirst;
	protected int hunger;

    public XieItemFood(String name, int i, int iconID, int healing, float nourishment, boolean isDogFood) {
        super(i-Xie.idShift,healing, nourishment, isDogFood);
        hunger=healing;
        thirst=0;
        setItemName(name);
        setIconIndex(iconID);
        useAction = EnumAction.eat;
    }
    
    public XieItemFood(String name, int i, int icon, int healing, float nourishment) {
    	this(name, i, icon, healing, nourishment, false);
    }
    
    public XieItemFood(String name, int i, int iconID, int healing, boolean isDogFood) {
    	this(name, i, iconID, healing, 0.6F, isDogFood);
    }
    
    public EnumAction getItemUseAction(ItemStack itemstack) { return useAction; }
    public void setAction(String s) {
    	s=s.trim().toLowerCase();
    	if (s.equals("eat") || s.equals("food")) useAction=EnumAction.eat;
    	else if (s.equals("drink")) useAction=EnumAction.drink;
    }
    
    public void setThirstAmount(int i) { thirst=i; }
    
    public void setEffects(List<PotionEffectWrapper> l) {
    	setEffects(-1,l);
    }
    
    public void setEffects(int meta, List<PotionEffectWrapper> l) {
    	if (effects==null) effects = new HashMap();
    	effects.put(meta, l); 
    }
 
    public int getIconFromDamage(int i)
    {
    	if (icon==null) return iconIndex;
    	
        if (icon.length>1 && i<icon.length) {
        	return icon[i];
        } else return icon[0];
    }
    
    @Override
	public void setIcons(int i[]) { icon=i; }
    
    /*public void setPotionEffect(PotionEffectWrapper e) {
    	if (e==null) return;
    	setPotionEffect(e.getPotionID(),e.getDuration(),e.getAmplifier(),e.getChance());
    }*/
    
    public ItemStack onFoodEaten(ItemStack itemstack, World world, EntityPlayer entityplayer) {        	
    	itemstack.stackSize--;
    	world.playSoundAtEntity(entityplayer, "random.burp", 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
    	
    	// satisfy hunger and/or quench thirst as appropriate
    	if (Thirsty.canHandleThirst && thirst>0) {
    		entityplayer.getFoodStats().addStats(hunger, getSaturationModifier());
    		Thirsty.quenchThirst(entityplayer, thirst, getSaturationModifier());
    	} else {
    		entityplayer.getFoodStats().addStats(getHealAmount(), getSaturationModifier());
    	}
        
    	
        if (world.isRemote) return itemstack;
    	// check potion effects
    	if (effects!=null && !effects.isEmpty()) {
    		List<PotionEffectWrapper> list = new LinkedList();
    		if (effects.containsKey(-1)) list.addAll(effects.get(-1));
    		if (effects.containsKey(itemstack.getItemDamage())) list.addAll(effects.get(itemstack.getItemDamage()));
            
    		for (PotionEffectWrapper e : list)
            	if (e.testChance(world.rand))
            		entityplayer.addPotionEffect(new PotionEffect(e));
        }

        if (drops==null || drops.isEmpty()) return itemstack;
        
        int x = (int)entityplayer.posX;
        int y = (int)entityplayer.posY;
        int z = (int)entityplayer.posZ;
        
        LinkedList<ItemStack> drop = new LinkedList(drops.getDrops());
        if (drop==null || drop.isEmpty()) return itemstack;
        
        if ((itemstack==null || itemstack.stackSize==0)) itemstack = drop.pop();
    	
        Xie.giveItemsToPlayer(world, entityplayer, drop);
    	
    	return itemstack;
    }
 
    @Override
	public void setDrops(Droppables dropz) {
		drops=dropz;
	}
	
	@Override
	public void addDrop(Drop drop) {
		drops.add(drop);
	}
	
	@Override
	public Droppables getDrops() { return drops; }
	
	@Override
	public Item setHasSubtypes(boolean b) {
		return super.setHasSubtypes(b);
	}
	
	@Override
	public void setNames(String n[]) {
		names=n;
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
