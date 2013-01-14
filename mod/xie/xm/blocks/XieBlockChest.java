package mod.xie.xm.blocks;

import java.util.List;
import java.util.Random;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import mod.xie.xm.util.XieTex;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.StepSound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;


public class XieBlockChest extends BlockChest implements IXieBlock {

	public boolean animate = true;
	public boolean mergeHorizontal = true;
	public boolean mergeVertical = false;
	public boolean vertical = false;
	public boolean jammable = true;
	
	protected String displayName;
	protected String largeName;
	
	protected XieChestRenderer renderer;
	
	protected String largeTex = "/item/largechest.png";
	protected String smallTex = "/item/chest.png";
	
	public XieBlockChest(String name, int id, String displayName) {
		super(id);
		setBlockName(name);
		renderType=22;
		this.displayName=displayName;
		renderer = new XieChestRenderer();
		Xie.registerTileEntity(XieTileEntityChest.class, displayName, renderer);
	}
	
	public boolean canPlaceBlockAt(World world, int i, int j, int k) {
		int count = 0;
		if (mergeHorizontal) {
			if (world.getBlockId(i - 1, j, k) == blockID) count++;
			if (world.getBlockId(i + 1, j, k) == blockID) count++;
			if (world.getBlockId(i, j, k - 1) == blockID) count++;
			if (world.getBlockId(i, j, k + 1) == blockID) count++;
		}
		if (mergeVertical) {
			if (world.getBlockId(i, j - 1, k) == blockID) count++;
			if (world.getBlockId(i, j + 1, k) == blockID) count++;
		}
        if (count > 1) return false;
        
        if (mergeHorizontal) {
        	if (neighbourCheck(world, i - 1, j, k)) return false;
        	if (neighbourCheck(world, i + 1, j, k)) return false;
        	if (neighbourCheck(world, i, j, k - 1)) return false;
        	if (neighbourCheck(world, i, j, k + 1)) return false;
        }
        
        if (mergeVertical) {
        	if (neighbourCheck(world, i, j - 1, k)) return false;
        	if (neighbourCheck(world, i, j + 1, k)) return false;
        }
        
        int targetBlockID = world.getBlockId(i, j, k);
        return targetBlockID == 0 || blocksList[targetBlockID].blockMaterial.isReplaceable();
    }

    protected boolean neighbourCheck(World world, int i, int j, int k) {
    	if (world.getBlockId(i, j, k) != blockID) return false;
    	
    	if (mergeHorizontal) {
	    	if (world.getBlockId(i - 1, j, k) == blockID) return true;
	        if (world.getBlockId(i + 1, j, k) == blockID) return true;
	        if (world.getBlockId(i, j, k - 1) == blockID) return true;
	        if (world.getBlockId(i, j, k + 1) == blockID) return true;
        }
    	
    	if (mergeVertical) {
    		if (world.getBlockId(i, j - 1, k) == blockID) return true;
    		if (world.getBlockId(i, j + 1, k) == blockID) return true;
    	}
    	
    	return false;
    }

    public void onBlockAdded(World world, int i, int j, int k) {
        unifyAdjacentChests(world, i, j, k);
        
        if (mergeHorizontal) {
        	unifyAdjacentChests(world, i, j, k - 1);
	        unifyAdjacentChests(world, i, j, k + 1);
	        unifyAdjacentChests(world, i - 1, j, k);
	        unifyAdjacentChests(world, i + 1, j, k);
        }
        
        if (mergeVertical) {
        	unifyAdjacentChests(world, i, j + 1, k);
        	unifyAdjacentChests(world, i, j - 1, k);
        }
        
        world.setBlockTileEntity(i, j, k, getBlockEntity());
    }

    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLiving entityliving) {
    	int playerFacing = MathHelper.floor_double((double)((entityliving.rotationYaw * 4F) / 360F) + 0.5D) & 3;
    	setFacing(world, i, j, k, playerFacing);
    }
    
    protected void setFacing(World world, int i, int j, int k, int playerFacing) {
    	byte meta = 0;
        if (playerFacing == 0) meta = 2;
        if (playerFacing == 1) meta = 5;
        if (playerFacing == 2) meta = 3;
        if (playerFacing == 3) meta = 4;
    	
        if (mergeHorizontal) {
	    	if (world.getBlockId(i, j, k - 1) == blockID) {
	    		if (meta==4 || meta==5) {
	    			world.setBlockMetadataWithNotify(i, j, k, meta);
	    			world.setBlockMetadataWithNotify(i, j, k - 1, meta);
	    			return;
	    		}
	    	}
	    	
	    	if (world.getBlockId(i, j, k + 1) == blockID) {
	    		if (meta==4 || meta==5) {
	    			world.setBlockMetadataWithNotify(i, j, k, meta);
	    			world.setBlockMetadataWithNotify(i, j, k + 1, meta);
	    			return;
	    		}
	    	}
	    	
	    	if (world.getBlockId(i - 1, j, k) == blockID) {
	    		if (meta==2 || meta==3) {
	    			world.setBlockMetadataWithNotify(i, j, k, meta);
	    			world.setBlockMetadataWithNotify(i - 1, j, k, meta);
	    			return;
	    		}
	    	}
	    	
	    	if (world.getBlockId(i + 1, j, k) == blockID) {
	    		if (meta==2 || meta==3) {
	    			world.setBlockMetadataWithNotify(i, j, k, meta);
	    			world.setBlockMetadataWithNotify(i + 1, j, k, meta);
	    			return;
	    		}
	    	}
        }
        
        if (vertical) {
        	if (meta==2) meta=0;
    		if (meta==3) meta=1;
    		if (meta==4) meta=6;
    		if (meta==5) meta=7;
        }
        
        if (mergeVertical) {
        	boolean below = world.getBlockId(i, j - 1, k) == blockID;
        	boolean above = world.getBlockId(i, j + 1, k) == blockID;
        	
        	if (above || below) {
	        	if (meta==2) meta=0;
	    		if (meta==3) meta=1;
	    		if (meta==4) meta=6;
	    		if (meta==5) meta=7;
	    		
	    		if (below) world.setBlockMetadataWithNotify(i, j - 1, k, meta);
	        	if (above) world.setBlockMetadataWithNotify(i, j + 1, k, meta);
        	}
        }
        
        world.setBlockMetadataWithNotify(i, j, k, meta);
    }
    
    public void unifyAdjacentChests(World world, int i, int j, int k) {
        if (world.isRemote)  return;

    	int west = world.getBlockId(i, j, k - 1);
    	int east = world.getBlockId(i, j, k + 1);
    	int south = world.getBlockId(i - 1, j, k);
    	int north = world.getBlockId(i + 1, j, k);
    	int above = world.getBlockId(i, j + 1, k);
    	int below = world.getBlockId(i, j - 1, k);
    	int meta = world.getBlockMetadata(i, j, k);
        
        if (mergeHorizontal && (west == blockID || east == blockID)) {
            int otherMeta = world.getBlockMetadata(i, j, west != blockID ? k + 1 : k - 1);
            if (otherMeta == 4) meta = 4; else meta = 5;
            int otherSouth = world.getBlockId(i - 1, j, west != blockID ? k + 1 : k - 1);
            int otherNorth = world.getBlockId(i + 1, j, west != blockID ? k + 1 : k - 1);
            if ((Block.opaqueCubeLookup[south] || Block.opaqueCubeLookup[otherSouth]) && !Block.opaqueCubeLookup[north] && !Block.opaqueCubeLookup[otherNorth]) meta = 5;
            if ((Block.opaqueCubeLookup[north] || Block.opaqueCubeLookup[otherNorth]) && !Block.opaqueCubeLookup[south] && !Block.opaqueCubeLookup[otherSouth]) meta = 4;
        } else if (mergeHorizontal && (south == blockID || north == blockID)) {
            int otherMeta = world.getBlockMetadata(south != blockID ? i + 1 : i - 1, j, k);
            if (otherMeta == 2) meta = 2; else meta = 3;
            int otherWest = world.getBlockId(south != blockID ? i + 1 : i - 1, j, k - 1);
            int otherEast = world.getBlockId(south != blockID ? i + 1 : i - 1, j, k + 1);
            if ((Block.opaqueCubeLookup[west] || Block.opaqueCubeLookup[otherWest]) && !Block.opaqueCubeLookup[east] && !Block.opaqueCubeLookup[otherEast]) meta = 3;
            if ((Block.opaqueCubeLookup[east] || Block.opaqueCubeLookup[otherEast]) && !Block.opaqueCubeLookup[west] && !Block.opaqueCubeLookup[otherWest]) meta = 2;
        } else if (mergeVertical && (above == blockID || below == blockID)) {
        	int otherMeta = world.getBlockMetadata(i, above == blockID ? j + 1 : j - 1, k);
        	meta = otherMeta;
    		int otherWest = world.getBlockId(i, above == blockID ? j + 1 : j - 1, k - 1);
    		int otherEast = world.getBlockId(i, above == blockID ? j + 1 : j - 1, k + 1);
    		int otherNorth = world.getBlockId(i + 1, above == blockID ? j + 1 : j - 1, k);
    		int otherSouth = world.getBlockId(i - 1, above == blockID ? j + 1 : j - 1, k);
    		
        	if ((Block.opaqueCubeLookup[west] || Block.opaqueCubeLookup[otherWest])
        		&& !Block.opaqueCubeLookup[east] && !Block.opaqueCubeLookup[otherEast]) meta = 1;  
        	if ((Block.opaqueCubeLookup[east] || Block.opaqueCubeLookup[otherEast])
            		&& !Block.opaqueCubeLookup[west] && !Block.opaqueCubeLookup[otherWest]) meta = 0;
        	if ((Block.opaqueCubeLookup[north] || Block.opaqueCubeLookup[otherNorth])
            		&& !Block.opaqueCubeLookup[south] && !Block.opaqueCubeLookup[otherSouth]) meta = 6;
        	if ((Block.opaqueCubeLookup[south] || Block.opaqueCubeLookup[otherSouth])
            		&& !Block.opaqueCubeLookup[north] && !Block.opaqueCubeLookup[otherNorth]) meta = 7;
        } else {
            if (mergeHorizontal) {
            	if (Block.opaqueCubeLookup[west] && !Block.opaqueCubeLookup[east]) meta = 3;
            	if (Block.opaqueCubeLookup[east] && !Block.opaqueCubeLookup[west]) meta = 2;
            	if (Block.opaqueCubeLookup[south] && !Block.opaqueCubeLookup[north]) meta = 5;
            	if (Block.opaqueCubeLookup[north] && !Block.opaqueCubeLookup[south]) meta = 4;
            }
        }
        
        world.setBlockMetadataWithNotify(i, j, k, meta);
    }
	
	public String getDisplayName() { return displayName; }
	public void setLargeName(String s) { largeName = s; }
	
	public void setSmallTex(String s) { smallTex = s; }
	public void setLargeTex(String s) { largeTex = s; }
	public String getSmallTex() { return smallTex; }
	public String getLargeTex() { return largeTex; }
	
	public TileEntity getBlockEntity() {
		return new XieTileEntityChest(this);
	}
	
	protected String openSound = "random.chestopen";
	public String getOpenSound() { return openSound; }
	public void setOpenSound(String s) { openSound = s; }
	
	protected String closeSound = "random.chestclosed"; 
	public String getCloseSound() { return closeSound; }
	public void setCloseSound(String s) { closeSound = s; }
	
	public boolean blockActivated(World world, int i, int j, int k, EntityPlayer entityplayer) {
		if (world.isRemote) return true;
		
		IInventory chestEntity = (XieTileEntityChest)world.getBlockTileEntity(i, j, k);
        if (chestEntity == null) return true;
        
    	// check for blocks above chest lid (or adjacent chest blocks) that might jam it shut
        if (!mergeVertical && (jammable || animate))
        	if (isChestJammed(world,i,j,k)) return true;
        
        if (mergeHorizontal) {
	        if (world.getBlockId(i - 1, j, k) == blockID) 
	        	chestEntity = getLargeChestInventory(world, i-1,j,k, chestEntity, true);
	        if (world.getBlockId(i + 1, j, k) == blockID) 
	        	chestEntity = getLargeChestInventory(world, i+1,j,k, chestEntity, false);
	        if (world.getBlockId(i, j, k - 1) == blockID) 
	        	chestEntity = getLargeChestInventory(world, i,j,k-1, chestEntity, true);
	        if (world.getBlockId(i, j, k + 1) == blockID) 
	        	chestEntity = getLargeChestInventory(world, i,j,k+1, chestEntity, false);
		}
        
        if (mergeVertical) {
        	if (world.getBlockId(i, j - 1, k) == blockID)
        		chestEntity = getLargeChestInventory(world, i,j-1,k, chestEntity, true);
        	if (world.getBlockId(i, j + 1, k) == blockID)
        		chestEntity = getLargeChestInventory(world, i,j+1,k, chestEntity, false);
        }
        
        entityplayer.displayGUIChest(((IInventory) (chestEntity)));
        return true;
    }
	
	protected boolean isChestJammed(World world, int i, int j, int k) {
		int meta = world.getBlockMetadata(i,j,k);
				
		switch (meta) {
		case 2: //
		case 3: // All with the lid facing up
		case 4: //
		case 5: //
			if (world.isBlockNormalCube(i, j + 1, k)) return true;
	        if (world.getBlockId(i - 1, j, k) == blockID && world.isBlockNormalCube(i - 1, j + 1, k)) return true;
	        if (world.getBlockId(i + 1, j, k) == blockID && world.isBlockNormalCube(i + 1, j + 1, k)) return true;
	        if (world.getBlockId(i, j, k - 1) == blockID && world.isBlockNormalCube(i, j + 1, k - 1)) return true;
	        if (world.getBlockId(i, j, k + 1) == blockID && world.isBlockNormalCube(i, j + 1, k + 1)) return true;
	        break;
		case 0: // facing -k
			if (world.isBlockNormalCube(i, j, k - 1)) return true;
			if (world.getBlockId(i, j - 1, k) == blockID && world.isBlockNormalCube(i, j - 1, k - 1)) return true;
	        if (world.getBlockId(i, j + 1, k) == blockID && world.isBlockNormalCube(i, j + 1, k - 1)) return true;
	        break;
		case 1: // facing +k
			if (world.isBlockNormalCube(i, j, k + 1)) return true;
			if (world.getBlockId(i, j - 1, k) == blockID && world.isBlockNormalCube(i, j - 1, k + 1)) return true;
	        if (world.getBlockId(i, j + 1, k) == blockID && world.isBlockNormalCube(i, j + 1, k + 1)) return true;
	        break;
		case 6: // facing -i
			if (world.isBlockNormalCube(i - 1, j, k)) return true;
			if (world.getBlockId(i, j - 1, k) == blockID && world.isBlockNormalCube(i - 1, j - 1, k)) return true;
	        if (world.getBlockId(i, j + 1, k) == blockID && world.isBlockNormalCube(i - 1, j + 1, k)) return true;
	        break;
		case 7: // facing +i
			if (world.isBlockNormalCube(i + 1, j, k)) return true;
			if (world.getBlockId(i, j - 1, k) == blockID && world.isBlockNormalCube(i + 1, j - 1, k)) return true;
	        if (world.getBlockId(i, j + 1, k) == blockID && world.isBlockNormalCube(i + 1, j + 1, k)) return true;
	        break;
		}
		
		return false;
	}
	
	protected IInventory getLargeChestInventory(World world, int i, int j, int k, IInventory base, boolean flag) {
		if (largeName==null) largeName=displayName;
		if (flag) return new InventoryLargeChest(largeName, (IInventory)world.getBlockTileEntity(i, j, k), base);
		else return new InventoryLargeChest(largeName, base, (IInventory)world.getBlockTileEntity(i, j, k));
	}
	
	public int getBlockTexture(IBlockAccess iblockaccess, int i, int j, int k, int l) {
		return getBlockTextureFromSide(l);
	}
	
	/* Xie Basic Block copy-pasta */
	protected Droppables drops = new Droppables();
	
	public boolean damageDrops;
	public boolean dropBlock = true;
	public boolean inheritMeta = true;
	
	protected XieTex tex;
	protected String[] names;
	
	public int renderType;
	public boolean renderNormally = true;
	protected Boolean isOpaque;
	
	protected int becomesWhenDestroyed[];
	
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
    
    @Override
	public void setTex(XieTex t) {
    	isDefaultTexture=t.getTexFile()==null;
    	tex=t;
    }
    
    @Override
	public int getBlockTextureFromSide(int i) {
    	return getBlockTextureFromSideAndMetadata(i,0);
    }
 
	// texture IDs, tex[side][meta]
    @Override
	public int getBlockTextureFromSideAndMetadata(int i, int j) {
    	return tex.getTexFromSideAndMeta(i, j);
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
    	if (becomesWhenDestroyed!=null) {
    		if (becomesWhenDestroyed.length>1)
    			world.setBlockAndMetadataWithNotify(i, j, k, becomesWhenDestroyed[0], becomesWhenDestroyed[1]);
    		else world.setBlockAndMetadataWithNotify(i, j, k, becomesWhenDestroyed[0], l);
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
		if (names==null || names.length <= i) return getBlockName();
		else return names[i];
	}

	@Override
	public void setNames(String[] string) {
		names = string;
	}

	@Override
	public String[] getNames() { return names; }
	
	@Override
	public String getTextureFile() {
		return Xie.textureWrapper(this);
	}

	@Override
	public void setBecomesWhenDestroyed(int[] bwd) {
		becomesWhenDestroyed=bwd;
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
}
