package xie.mods.xm.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;

import xie.mods.xm.util.BlockID;
import xie.mods.xm.util.Drop;
import xie.mods.xm.util.Droppables;
import xie.mods.xm.util.Xie;


public class XieItemTool extends ItemTool implements XieItemInterface {
	protected int icon[];
	protected String names[];
	protected Droppables drops;
	
	protected ArrayList<Block> effectivityB = new ArrayList();
	protected Material effectivityM[];
	protected ArrayList<Block> canHarvest = new ArrayList();
	protected ArrayList<Block> cannotHarvest = new ArrayList();
	protected boolean ultimate = false;
	protected boolean magicWand = false;
	
	public Map<BlockID,BlockID> transforms = new HashMap();
	
	// constructors
	public XieItemTool(String name, int id, int index, int dmg, EnumToolMaterial toolMaterial) {
		super(id-Xie.idShift,dmg,toolMaterial,null);
        setIconIndex(index);
        setItemName(name);
	}
	
	// setters
    public void makeUltimate() { ultimate=true; }
    public void setBlockEffectivity(Block blocks[]) { for (Block b : blocks) effectivityB.add(b); }
    public void setMaterialEffectivity(Material mats[]) { effectivityM=mats; }
    
    public void setHarvestables(Block blocks[], boolean yesWeCan) { 
    	for (Block b : blocks) {
    		if (yesWeCan) canHarvest.add(b);
    		else cannotHarvest.add(b);
    	}
    }
    
    public void setEfficiency(float f) { efficiencyOnProperMaterial=f; }
	public void makeMagic() { magicWand=true;} 
	
	// overrides
    public float getStrVsBlock(ItemStack itemstack, Block block) {
    	if (isEffective(block)) return efficiencyOnProperMaterial;
    	else return 1.0F;
    }
    
    protected boolean isEffective(Block block) {
    	if (ultimate) return true;
    	
    	if (effectivityM!=null)
    		for (Material m : effectivityM) 
    			if (block.blockMaterial==m) return true;
    	
    	if (effectivityB!=null)
    		for (Block b : effectivityB)
    			if (block==b) return true;

        return false;
    }
    
    public boolean canHarvestBlock(Block block)
    {
    	if (ultimate) return true;
    	
    	if (cannotHarvest!=null)
    		for (Block b : cannotHarvest) if (block==b) return false;
    	
    	if (canHarvest==null) return true;
    	
    	for (Block b : canHarvest) if (block==b) return true;
    	return false;
    }
    
    public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, int i, int j, int k, int l) {
    	if (magicWand) {
    		int m = world.getBlockMetadata(i, j, k) + 1;
    		if (m>15) m=0;
    		Xie.setBlockMeta(world,i, j, k, m);
    	}
    	
    	if(!entityplayer.canPlayerEdit(i, j, k, l, itemstack)) return false;
    	
    	if (!transforms.isEmpty()) {
    		BlockID blockID = getTransform(world, i, j, k);
	    	if (l!=0 && blockID!=null) {
	    		Block block;
	    		if (blockID.blockID==0) block = Block.blocksList[world.getBlockId(i,j,k)];
	    		else block = blockID.getBlock();
	    		Xie.playBreakSoundEffect(world, block, i + 0.5F, j + 0.5F, k + 0.5F);
	    		if (world.isRemote) return true;
	    		if (blockID.hasMeta()) world.setBlockAndMetadataWithNotify(i, j, k, blockID.blockID, blockID.metadata);
	    		else world.setBlockWithNotify(i, j, k, blockID.blockID);
	    		itemstack.damageItem(1, entityplayer);
	            return true;
	    	}
    	}
    	
    	return false;
    }
    
    // new methods
    protected BlockID getTransform(World world, int i, int j, int k) {
    	for (BlockID b : transforms.keySet()) {
    		if (b.matches(world, i, j, k)) return transforms.get(b);
    	}
    	
    	return null;
    }
    

	public void addBlock(Block block) {
		effectivityB.add(block);
		canHarvest.add(block);
	}
    	
	// copy-pasta from basic items
	
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
