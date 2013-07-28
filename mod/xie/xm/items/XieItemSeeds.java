package mod.xie.xm.items;


import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.Icon;
import net.minecraft.world.World;

// Referenced classes of package net.minecraft.src:
//            Item, World, Block, ItemStack, 
//            EntityPlayer

public class XieItemSeeds extends ItemSeeds implements XieItemInterface {
    protected Block growsInto;
    protected int growsIntoMeta;
    protected Droppables drops;
    protected String[] names;
    
    public XieItemSeeds(int id, Block plant, String name)
    {
        super(id-Xie.idShift,plant.blockID,0);
        growsInto = plant;
        growsIntoMeta = 0;
        setUnlocalizedName(name);
        drops=null;
    }
    
    public void setGrowsInto(Block b) {
    	growsInto=b;
    }
    
    public void setGrowsInto(Block b, int meta) {
    	growsInto=b;
    	growsIntoMeta=meta;
    }
    
    public void setGrowsIntoMeta(int meta) {
    	growsIntoMeta=meta;
    }

 // TODO FTFESAD WTF is with these floats?
    @Override
    public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, int i, int j, int k, int l, float a, float b, float c) {
    	// check mouse button, we're only going for a right-click
        if(l != 1)
        {
            return false;
        }
        if(growsInto.canPlaceBlockAt(world, i, j + 1, k)) {
           Xie.setBlockAndMeta(world, i, j + 1, k, growsInto.blockID, (growsIntoMeta<0)?itemstack.getItemDamage():growsIntoMeta);
            growsInto.setBlockBoundsBasedOnState(world,i,j,k);
            itemstack.stackSize--;
            if (drops!=null) Xie.giveItemsToPlayer(world, entityplayer, drops.getDrops());
            return true;
        } else
        {
            return false;
        }
    }
    
    @Override
	public Item getItem() {
    	return this;
    }

	@Override
	public void setDrops(Droppables drops) {
		this.drops=drops;
	}

	@Override
	public void addDrop(Drop drop) {
		if (drops==null) drops = new Droppables(drop);
		else drops.add(drop);
	}
	
	@Override
	public void setNames(String n[]) { 
		names=n;
		if (n.length>0) {
			this.setHasSubtypes(true);
		}
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
	
	protected XieTex icon;
	@Override
    public Icon getIconFromDamage(int i) {
		return icon.getIconFromDamage(i);
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
