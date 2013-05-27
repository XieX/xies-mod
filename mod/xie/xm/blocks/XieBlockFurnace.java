package mod.xie.xm.blocks;

import java.util.HashMap;

import mod.xie.xm.XiesMod;
import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.StepSound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Icon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import java.util.List;
import java.util.Map;
import java.util.Random;



import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.src.ModLoader;
import net.minecraftforge.common.ForgeDirection;


public class XieBlockFurnace extends BlockContainer implements IXieBlock {
	protected String name;
	protected String displayName;
    protected Random rand;
    protected final boolean isActive;
    protected int metaByBurnTime[];
    protected int burnLevelScaleMax = 1200;
    protected int requiredCookTime = 200;
    protected int burnLevel;
    protected XieTileEntityFurnace furnace;
    public int initialBurnTime = 0;
    public int guiID = 69;
    
    protected Block active;
    protected Block idle;
    
	public Map<ItemWrapper,Integer> fuels = new HashMap();
	public Map<ItemWrapper, ItemWrapper> smeltables = new HashMap();
	public boolean smeltsAsFurnace = false;
	public boolean fuelsAsFurnace = false;
	public boolean naked = false;
	protected int volatility = 0;
	
	public XieBlockFurnace(String blockName, int id, String displayName, boolean active) {
		super(id, Material.rock);
		name = blockName;
		setUnlocalizedName(blockName);
		this.displayName=displayName;
		isActive=active;
		if (isActive) this.active = this;
		else this.idle = this;
		ModLoader.registerTileEntity(XieTileEntityFurnace.class, displayName);
		setTickRandomly(true);
		this.setCreativeTab(CreativeTabs.tabDecorations);
	}
	
	// Getters and Setters
	public void setIdleVersion(Block b) {idle = b;}
	public String getDisplayName() { return displayName; }
    public TileEntity getBlockEntity(int md) { return getBlockEntity(); }
    public TileEntity getBlockEntity() { return new XieTileEntityFurnace(); }
    public void setBurnLevelScaleMax(int m) { burnLevelScaleMax=m; }
    public void setBurnLevels(int levels[]) {metaByBurnTime=levels;}
    public boolean metaVariesByBurnTime() { return metaByBurnTime!=null; }
    public boolean burnsOut() { return idle==null; }
	public void setCookTime(int c) { requiredCookTime=c; }
	public int getRequiredCookTime() { return requiredCookTime; }
	public int getBurnLevelScaleMax() { return burnLevelScaleMax; }
    
    // New Methods
	private static int counter = 0;
    public void updateState(int burnLevel, World world, int i, int j, int k) {
        TileEntity tileentity = world.getBlockTileEntity(i, j, k);
        
        if (metaVariesByBurnTime()) scaleBurnLevel(burnLevel, world, i, j, k);
        int l = world.getBlockMetadata(i, j, k);
        if (burnLevel > 0) Xie.setBlockAndMeta(world, i, j, k, active.blockID, l);
        else if (idle!=null) Xie.setBlockAndMeta(world, i, j, k, idle.blockID, l);
        else {
        	breakBlock(world, i, j, k, blockID, l);
        	return;
        }
        
        if(tileentity != null) {
            tileentity.validate();
            world.setBlockTileEntity(i, j, k, tileentity);
        }
        
        if (counter++>50) {
        	counter=0;
        	if (naked || volatility>0) onlyYouCanPreventWildfires(world,i,j,k);
        }
    }
    
    protected void scaleBurnLevel(int burnTime, World world, int i, int j, int k) {
    	// determine burn level
    	if (burnTime>0)
    		burnLevel = (int)Math.floor(metaByBurnTime.length*burnTime/burnLevelScaleMax);
    	else burnLevel=0;
    	
    	// validate array bounds
    	if (burnLevel>=metaByBurnTime.length) burnLevel=metaByBurnTime.length-1;
    	
    	// scale and set meta based on burn level
    	if (world.getBlockMetadata(i,j,k)!=burnLevel) {
    		world.setBlockMetadataWithNotify(i,j,k,metaByBurnTime[burnLevel<0?0:burnLevel], 3);
    		setBlockBoundsBasedOnState(world, i, j, k);
    	}
    		
    }
    
    protected boolean exposedToRain(World world, int i, int j, int k) {
    	return world.isRaining() && 
    			(world.canLightningStrikeAt(i, j, k) 
    			|| world.canLightningStrikeAt(i - 1, j, k) 
    			|| world.canLightningStrikeAt(i + 1, j, k) 
    			|| world.canLightningStrikeAt(i, j, k - 1) 
    			|| world.canLightningStrikeAt(i, j, k + 1));
    }

    // Overrides and such
    public void onlyYouCanPreventWildfires(World world, int i, int j, int k) {
  
    	if (naked) {
            if (exposedToRain(world, i, j, k)) {
            	breakBlock(world, i, j, k, blockID, world.getBlockMetadata(i, j, k));
                return;
            }
    	}
    	
    	if (volatility>0) {
  
      	Random random = new Random();
    	if (random.nextInt(10)>=volatility) return;
    		for (int n=0; n<volatility; n++) {
    			ForgeDirection dir = Xie.getRandomDir(random);
    			int x = i + dir.offsetX;
    			int y = j + dir.offsetY;
    			int z = k + dir.offsetZ;
					    			
    			if (Block.fire.canBlockCatchFire(world, x, y, z, dir))
    				Xie.setBlockAndMeta(world, x, y, z, Block.fire.blockID, 0);
    		}
    	}
    }
    
    public void setVolatility(int n) {
    	if (n>10) n=10;
    	volatility = n;
    }

    @Override
    public void onBlockAdded(World world, int i, int j, int k) {
        super.onBlockAdded(world, i, j, k);
        if (renderType!=1) setDefaultDirection(world, i, j, k);
        // Added in super class //world.setBlockTileEntity(i, j, k, getBlockEntity(world.getBlockMetadata(i,j,k)));
    }

    @Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
    	if (!world.isAirBlock(i,j,k)) return false;
    	if (!world.isBlockOpaqueCube(i, j - 1, k)) return false;
    	if (!world.isBlockNormalCube(i, j-1, k)) return false;
    	
		if (canBePlacedOn==null) return super.canPlaceBlockAt(world, i, j, k);
		if (canBePlacedOn.isEmpty()) return super.canPlaceBlockAt(world, i, j, k);
		int blockId = world.getBlockId(i, j, k);
		if (blockId<0) return false;
		Block b = Block.blocksList[world.getBlockId(i, j, k)];
		return canBePlacedOn.contains(b);
    }
    
    @Override
    public void randomDisplayTick(World world, int i, int j, int k, Random random) {
        if(!isActive) return;

        float x;
        float y;
        float z;
        
        if (renderType==1) {
        	// set particle co-ords for anywhere within block
        	x = i + random.nextFloat();
        	y = j + random.nextFloat();
        	z = k + random.nextFloat();
        } else {
        	// set particle co-ords based on orientation
	        int orientation = world.getBlockMetadata(i, j, k);
	        float f = i + 0.5F;
	        float f1 = j + 0.0F + (random.nextFloat() * 6F) / 16F;
	        float f2 = k + 0.5F;
	        float f3 = 0.52F;
	        float f4 = random.nextFloat() * 0.6F - 0.3F;
	        switch (orientation) {
        		case 2: x=f + f4; y=f1; z=f2 - f3; break;
        		case 3: x=f + f4; y=f1; z=f2 + f3; break;
	        	case 4: x=f - f3; y=f1; z=f2 + f4; break;
	        	case 5: x=f + f3; y=f1; z=f2 + f4; break;
	        	default: 
	        		x = i + random.nextFloat();
	            	y = j + random.nextFloat();
	            	z = k + random.nextFloat();
	        }
        }
        
        world.spawnParticle("flame",x,y,z,0,0,0);
        world.spawnParticle("smoke",x,y,z,0,0,0);
    }

    @Override
    public boolean onBlockActivated(World world, int i, int j, int k, EntityPlayer player, int wtf, float a, float b, float c) {
        player.openGui(XiesMod.get(), 69, world, i, j, k);
        
        return true;
    }
    
	@Override
	public Object getGuiElement(EntityPlayer player, World world, int i, int j, int k) {
	    XieTileEntityFurnace furnace = (XieTileEntityFurnace)world.getBlockTileEntity(i, j, k);
		return new XieGuiFurnace(player.inventory, furnace);
	}
    
	@Override
    public void breakBlock(World world, int i, int j, int k, int id, int meta) {
        ejectContents(world,i,j,k);
        super.breakBlock(world, i, j, k, id, meta);
        
    	if (becomesWhenDestroyed!=null && becomesWhenDestroyed[0]>=0) {
    		if (becomesWhenDestroyed.length>1 && becomesWhenDestroyed[1]>=0) {
    			Xie.setBlockAndMeta(world, i, j, k, becomesWhenDestroyed[0], becomesWhenDestroyed[1]);
    		} else {
    			Xie.setBlock(world, i, j, k, becomesWhenDestroyed[0]);
    		}
    	} else {
    		Xie.setBlock(world, i, j, k, 0);
    	}
    }

	@Override
	public void onEntityCollidedWithBlock(World world, int i, int j, int k, Entity entity)
    {
    	if (isSpikey && !world.isRemote) entity.attackEntityFrom(DamageSource.generic, 1);
    	
    	if (naked && !world.isRemote) {
    		if (entity instanceof EntityLiving) {
    			int fire = burnLevel*burnLevelScaleMax/metaByBurnTime.length/80;
    			if (fire<2) fire=2;
    			entity.setFire(fire);
    		} else entity.setFire(2);
    	}
    	
    	super.onEntityCollidedWithBlock(world, i, j, k, entity);
    }
    
    public void ejectContents(World world, int i, int j, int k) {
    	XieTileEntityFurnace tileentityfurnace = (XieTileEntityFurnace)world.getBlockTileEntity(i, j, k);
        if(tileentityfurnace != null) {
        	for(ItemStack itemstack : tileentityfurnace.inventory())
        		Xie.spawnItem(itemstack, world, i, j, k);
        }
    }
	
	// Copied Methods
    protected void setDefaultDirection(World world, int i, int j, int k) {
    	if (renderType!=0) return;
        if(world.isRemote) return;

        int south = world.getBlockId(i, j, k - 1);
        int north = world.getBlockId(i, j, k + 1);
        int east = world.getBlockId(i - 1, j, k);
        int west = world.getBlockId(i + 1, j, k);
 
        byte direction = 3;
        if (south>=0 && north>=0 && east>=0 && west>=0) {
        	// if(Block.opaqueCubeLookup[south] && !Block.opaqueCubeLookup[north]) direction = 3;
        	if(Block.opaqueCubeLookup[north] && !Block.opaqueCubeLookup[south]) direction = 2;
        	if(Block.opaqueCubeLookup[east] && !Block.opaqueCubeLookup[west]) direction = 5;
        	if(Block.opaqueCubeLookup[west] && !Block.opaqueCubeLookup[east]) direction = 4;
        }
        
        Xie.setBlockMeta(world, i, j, k, direction);
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
		if (blockBoundsByMeta.length>meta) setBlockBounds(blockBoundsByMeta[meta<0?0:meta]);
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
 
	// texture IDs, tex[side][meta]
    @Override
	public Icon getIcon(int i, int j) {
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
    	if (world.isRemote) return;
    	if (becomesWhenDestroyed!=null) {
    		if (becomesWhenDestroyed.length>1)
    			Xie.setBlockAndMeta(world, i, j, k, becomesWhenDestroyed[0], becomesWhenDestroyed[1]);
    		else Xie.setBlock(world, i, j, k, becomesWhenDestroyed[0]);
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
		if (names==null || names.length <= i || i < 0) return getUnlocalizedName();
		else return names[i];
	}

	@Override
	public void setNames(String[] string) {
		names = string;
	}

	@Override
	public String[] getNames() { return names; }

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
	public TileEntity createNewTileEntity(World var1) {
		return this.getBlockEntity();
	}
	
	@Override
	public void registerIcons(IconRegister registrar) {
		tex = AutomagicTextureFactory.getBlock(registrar, getUnlocalizedName2());
	}
}
