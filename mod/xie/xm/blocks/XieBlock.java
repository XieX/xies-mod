package mod.xie.xm.blocks;

import java.util.List;
import java.util.Random;

import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.StepSound;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;


public class XieBlock extends Block implements IXieBlock {
	
	public XieBlock(int id, String n) {
		this(id, Material.wood, n);
	}
	
	public XieBlock(int id, Material mat, String n) {
		super(id, mat);
		setUnlocalizedName(n);
		this.setCreativeTab(CreativeTabs.tabBlock);
	}

	protected Droppables drops = new Droppables();
	
	public boolean damageDrops;
	public boolean dropBlock = true;
	public boolean inheritMeta = true;
	
	protected XieTex tex;
	protected String[] names;
	
	public int renderType;
	public boolean renderNormally = true;
	protected Boolean isOpaque;
	
	protected ItemWrapper becomesWhenDestroyed;
	
	protected float blockBounds[];
	protected float blockBoundsByMeta[][];
	
	@Override
	public Block getBlock() {
		return this;
	}

	@Override
	public void setBlockBounds(float boundsByMeta[][]) { blockBoundsByMeta=boundsByMeta; }

	public float[] getBlockBounds() { return blockBounds; }
	public boolean boundsVaryByMeta() { return blockBoundsByMeta!=null; }

	protected void updateBoundsForMeta(int meta) {
		if (blockBoundsByMeta==null) return;
		if (blockBoundsByMeta.length>meta) setBlockBounds(blockBoundsByMeta[meta]);
		else setBlockBounds(blockBoundsByMeta[blockBoundsByMeta.length-1]);
	}
	
	protected List<Block> canBePlacedOn;
	@Override
	public void setCanBePlacedOn(List<Block> blocks) {
		canBePlacedOn = blocks;
	}	
	
	@Override
	public boolean canPlaceBlockAt(World world, int i, int j, int k) {
		if (canBePlacedOn==null) return super.canPlaceBlockAt(world, i, j, k);
		if (canBePlacedOn.isEmpty()) return super.canPlaceBlockAt(world, i, j, k);
		Block b = Block.blocksList[world.getBlockId(i, j, k)];
		return canBePlacedOn.contains(b);
    }
	
	@Override
	public void addDrop(Drop drop) {
		drops.add(drop);
	}
	
	@Override
	public void setDrops(Droppables drops) {
		this.drops=drops;
	}
	
	@Override
	public int getRenderType()
    {
        return renderType;
    }
    
    @Override
	public boolean renderAsNormalBlock()
    {
    	return renderNormally;
    }
    
    @Override
	public void setRenderType(int r) {
    	renderType=r;
    	renderNormally=false;
    	if (Xie.isTransparentRenderType(r)) setIsOpaque(false);
    }
    
    @Override
	public boolean isOpaqueCube() {
    	if (isOpaque==null) isOpaque = true;
        return isOpaque;
    }
    
    @Override
	public XieTex getTex() {
    	return tex;
    }
 
	// texture IDs, tex[side][meta]
    @Override
	public Icon getIcon(int i, int j) {
    	return tex.getIcon(i, j);
    }
    
    // not really used
    @Override
	public int quantityDropped(Random random) {
        return 0;
    }

    // not really used
    @Override
	public int idDropped(int i, Random random, int meta) {
    	return -1;
    }
    
    @Override
	public void onBlockDestroyedByPlayer(World world, int i, int j, int k, int l) {
    	if (becomesWhenDestroyed!=null && becomesWhenDestroyed.isBlock()) {
    		if (becomesWhenDestroyed.hasMeta())
    			Xie.setBlockAndMeta(world, i, j, k, becomesWhenDestroyed.id, becomesWhenDestroyed.meta);
    		else Xie.setBlockAndMeta(world, i, j, k, becomesWhenDestroyed.id, 0); // TODO preserve meta value of previous block??
    	}
    }
    
    @Override
	public void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float f, int multiplier) {
        if (world.isRemote) return;
    	
    	if (drops==null) {
        	// no drops defined, drop self if required
        	if (dropBlock) {
        		if (inheritMeta) Xie.spawnItem(new ItemStack(this,1,l), world, i, j, k); 
        		else Xie.spawnItem(new ItemStack(this), world, i, j, k);
        	}
        	return;
        } else drops.dropBlockAsItemWithChance(world, i, j, k, l, f, multiplier);
    }
    
    @Override
	public void setBlockBoundsBasedOnState(IBlockAccess iblockaccess, int i, int j, int k) {
    	if (blockBoundsByMeta!=null) 
    		updateBoundsForMeta(iblockaccess.getBlockMetadata(i, j, k));
	}

    @Override
	public int damageDropped(int i) {
        if (damageDrops) return i;
    	return 0;
    }
	
    @Override
	public Block setStepSound(StepSound stepsound) {
        return super.setStepSound(stepsound);
    }
    
    @Override
	public Block setHardness(float f) {
    	return super.setHardness(f);
    }

    @Override
	public void setBlockBounds(float[] bounds) {
		blockBounds = bounds;
		if (bounds==null || bounds.length<6) return;
		else setBlockBounds(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]);
		
		for (int i=0; i<6; i++) {
			if (i<3?bounds[i]!=0:bounds[i]!=1) {
				setIsOpaque(false);
				break;
			}
		}
	}    
	
	@Override
	public void setIsOpaque(boolean b) { 
    	isOpaque=b;
    	renderNormally=false;
    	Block.opaqueCubeLookup[blockID]=b;
    	if (!b) Block.lightOpacity[blockID]=0;
    	else Block.lightOpacity[blockID]=255;
    }

	@Override
	public boolean hasSubTypes() {
		return names!=null;
	}

	@Override
	public String getBlockNameIS(ItemStack itemstack) {
		int i = itemstack.getItemDamage();
		if (names==null || names.length <= i) return getUnlocalizedName();
		else return names[i];
	}

	@Override
	public void setNames(String[] string) {
		names = string;
	}

	@Override
	public String[] getNames() { return names; }

	@Override
	@Deprecated
	public void setBecomesWhenDestroyed(int[] bwd) {
		if (bwd.length>1) setBecomesWhenDestroyed(bwd[0],bwd[1]);
		else if (bwd.length==1) setBecomesWhenDestroyed(bwd[0]);
	}
	
	public void setBecomesWhenDestroyed(int id) {
		setBecomesWhenDestroyed(new ItemWrapper(id));
	}
	
	public void setBecomesWhenDestroyed(int id, int meta) {
		setBecomesWhenDestroyed(new ItemWrapper(id,meta));
	}
	
	public void setBecomesWhenDestroyed(ItemWrapper w) {
		becomesWhenDestroyed = w;
	}
	
	protected boolean walkThroughable = false;
	@Override
	public void walkThroughable(boolean b) {walkThroughable=b;}
	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int i, int j, int k) {
	   	if (walkThroughable) return null;
    	else return super.getCollisionBoundingBoxFromPool(world, i, j, k);
    }
    
	@Override
	public Block setLightValue(float f) { return super.setLightValue(f); }
    
	@Override
	public Block setLightOpacity(int i) { return super.setLightOpacity(i); }

	protected boolean isSpikey = false;
	
	@Override
	public void setIsSpikey(boolean b) { isSpikey = b; }
	
	@Override
	public void onEntityCollidedWithBlock(World world, int i, int j, int k, Entity entity) {
	    if (isSpikey && !world.isRemote) entity.attackEntityFrom(DamageSource.generic, 1);
	    super.onEntityCollidedWithBlock(world, i, j, k, entity);
	}

	@Override
	public Object getGuiElement(EntityPlayer player, World world, int i, int j, int k) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void registerIcons(IconRegister registrar) {
		tex = AutomagicTextureFactory.getBlock(registrar, getUnlocalizedName());
		if (textureBase!=null) tex = tex.setTextureBase(textureBase);
	}

	@Override
	public void setTextureBase(Block b) {
		textureBase = b;
	}
	protected Block textureBase;
}
