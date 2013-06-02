package mod.xie.xm.blocks;

import java.util.List;

import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.BlockID;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockPane;
import net.minecraft.block.StepSound;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Icon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import java.util.Random;


public class XieBlockGrass extends BlockGrass implements IXieBlock {

	public boolean grassCanDie = true;
	public boolean enableGrassColoring = true;
	public boolean useVanillaTexture = false;
	protected BlockID dirtBlock;
	protected int lightLevel = 8;
	
	public XieBlockGrass(String name, int id) {
		super(id);
		setUnlocalizedName(name);
		setStepSound(Block.soundGrassFootstep);
		setHardness(0.6F);
		dirtBlock = new BlockID(Block.dirt.blockID);
	}
	
	public void setDirtBlock(BlockID b) { dirtBlock = b; }
	public void setLightLevel(int i) { lightLevel = i; }

	public void updateTick(World world, int i, int j, int k, Random random) {
        if (world.isRemote) return;

        // check for adequate light, otherwise turn to dirt
        if (grassCanDie) {
        	if (world.getBlockLightValue(i, j + 1, k) < 4 && Block.lightOpacity[world.getBlockId(i, j + 1, k)] > 2) {
        		dirtBlock.set(world,i, j, k);
            	return;
        	} 
        }
        
        if (world.getBlockLightValue(i, j + 1, k) >= lightLevel) {
            for (int l = 0; l < 4; l++) {
                int x = (i + random.nextInt(3)) - 1;
                int y = (j + random.nextInt(5)) - 3;
                int z = (k + random.nextInt(3)) - 1;
                int aboveBlock = world.getBlockId(x, y + 1, z);
                if (world.getBlockId(x, y, z) == dirtBlock.blockID && world.getBlockLightValue(x, y + 1, z) >= 4 && Block.lightOpacity[aboveBlock] <= 2)
                	Xie.setBlock(world, x, y, z, blockID);
            }
        }
	}
    
    public void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float f, int multiplier) {
        if (world.isRemote) return;
    	
    	if (drops==null) {
        	// no drops defined, drop dirt by default   		
        	Xie.spawnItem(dirtBlock.getItemStack(), world, i, j, k);
        } else drops.dropBlockAsItemWithChance(world, i, j, k, l, f, multiplier);
    }
 
    // colourisation
    public int getBlockColor() {
    	if (enableGrassColoring) return super.getBlockColor();
    	else return 0xffffff;	
    }
    
    public int getRenderColor(int i) {
    	if (enableGrassColoring) return super.getRenderColor(i);
    	else return 0xffffff;
    }

    public int colorMultiplier(IBlockAccess block, int i, int j, int k) {
    	if (enableGrassColoring) return super.colorMultiplier(block,i,j,k);
    	else return 0xffffff;
    }
    
	// texture IDs, tex[side][meta]
    public Icon getIcon(int i, int j) {
    	if (useVanillaTexture) Block.grass.getIcon(i, j);
    	return tex.getIcon(i, j);
    }
	
	// vanilla grass block texturisation
	public int getVanillaBlockTextureFromSideAndMetadata(int i, int j) {
        if (i == 1) return 0;
        return i != 0 ? 3 : 2;
    }

    protected int getVanillaBlockTexture(IBlockAccess iblockaccess, int i, int j, int k, int l) {
        if (l == 1) return 0;
        if (l == 0) return 2;
        Material material = iblockaccess.getBlockMaterial(i, j + 1, k);
        return material != Material.snow && material != Material.craftedSnow ? 3 : 68;
    }
    
    // copy-pasta from XieBlock
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
    			Xie.setBlockAndMeta(world, i, j, k, becomesWhenDestroyed[0], becomesWhenDestroyed[1]);
    		else Xie.setBlockAndMeta(world, i, j, k, becomesWhenDestroyed[0], l);
    	}
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
	
	@Override
	public void registerIcons(IconRegister registrar) {
		tex = AutomagicTextureFactory.getBlock(registrar, getUnlocalizedName2());
		if (textureBase!=null) tex = tex.setTextureBase(textureBase);
	}

	@Override
	public void setTextureBase(Block b) {
		textureBase = b;
	}
	protected Block textureBase;
}