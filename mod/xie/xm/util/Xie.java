package mod.xie.xm.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;



import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;


import mod.xie.xm.Work;
import mod.xie.xm.XiesMod;
import mod.xie.xm.blocks.BlockSide;
import mod.xie.xm.blocks.IXieBlock;
import mod.xie.xm.blocks.XieBlockPlant;
import mod.xie.xm.blocks.XieChestRenderer;
import mod.xie.xm.blocks.XieTileEntityChest;
import mod.xie.xm.file.XieLog;
import mod.xie.xm.items.XieItemBucket;
import mod.xie.xm.items.XieItemInterface;
import mod.xie.xm.parser.DungeonLootHelper;
import mod.xie.xm.tex.XieTex;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderEngine;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.Potion;
import net.minecraft.src.BaseMod;
import net.minecraft.src.ModLoader;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.DungeonHooks;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;

public class Xie {
	public static Map<String,List<ItemWrapper>> aliasMap = new HashMap();
	public static Map<String,ItemWrapper> translationMap = new HashMap();
	
	public static int idShift = 256; //Block.blocksList.length; 
	
	public static int[] arrayAppend(int a[], int b) {
		int old[] = a;
		a = new int[a.length+1];
		for (int i=0; i<old.length; i++) a[i]=old[i];
		a[old.length] = b;
		return a;
	}
	
	public static int parseBlockID(String string) {
		if (string.equals("air")) return 0;
		
		Block b = parseBlock(string);
		if (b==null) return -1;
		else return b.blockID;
	}
	
	public static Block parseBlock(String string) {
		string=string.trim();
		if (isNumbers(string)) {
			int blockID = Integer.parseInt(string);
			if (blockID<Block.blocksList.length) {
				if (Block.blocksList[blockID]!=null) {
					return Block.blocksList[blockID];
				}
			}
			return null;
		}
		
		// check alias/translations
		ItemWrapper iw = aliasSearch(string);
		if (iw!=null) {
			Block b = iw.getBlock();
			if (b!=null) return b;
		}
		
		iw = translationLookup(string);
		if (iw!=null) {
			Block b = iw.getBlock();
			if (b!=null) return b;
		}
		
		for (int i=0; i<Block.blocksList.length; i++) {
			if (Block.blocksList[i]==null) continue;
			if (Block.blocksList[i].getUnlocalizedName()==null) continue;
			if (Block.blocksList[i].getUnlocalizedName().equals(string)) return Block.blocksList[i];
			String test = cleanName(Block.blocksList[i].getUnlocalizedName());
			if (test.equals(string)) {
				return Block.blocksList[i];
			}
		}
		return null;
	}
	
	public static int[] parseBlockAndMeta(String string) {
		BlockID blockID = parseBlockAndMetadata(string);
		
		if (blockID.hasMeta()) return new int[]{blockID.blockID,blockID.metadata};
		else return new int[]{blockID.blockID};
	}
	
	public static BlockID parseBlockAndMetadata(String string) {
		int meta = -1;
		if (string.contains(":")) {
			String split[] = string.split(":");
			string = split[0];
			if (split.length>1) {
				meta = Integer.parseInt(split[1]);
				meta = validateMeta(meta);
			}
		}
		
		int blockID = parseBlockID(string);
		
		return new BlockID(blockID, meta);
	}
	

    public static List parseItemStacks(String string)
    {
        List list = new LinkedList();
        String item[] = string.split(",");
        for(String s : item)
        {
            ItemStack i = parseItemStack(s);
            if(i != null) {
                list.add(i);
            } else {
            	XieLog.log().logDelayed("Failed on parsing itemstack "+s);
            	return null;
            }
        }

        return list;
    }
    
    public static List<ItemWrapper> parseItemWrappers(String string) {
    	if (string==null) return null;
    	
    	List<ItemWrapper> list = new LinkedList();
    	
    	String strings[] = string.split(",");
    	for (String s : strings) {
    		ItemWrapper item = parseItemWrapper(s);
    		if (s==null) {
    			return null;
    		}
    		list.add(item);
    	}
    	
    	return list;
    }
    
    // parses an item or itemstack of the format "item", "item:count", "item:count:meta"
    public static ItemWrapper parseItemWrapper(String string) {
    	ItemStack is = Xie.parseItemStack(string);
    	if (is==null) return null;
    	else return new ItemWrapper(is);
    }
    
    // parses an itemwrapper of the form "id_meta", using numbers not identifiers
	public static ItemWrapper parseImplicitItemWrapper(String string) {
		int id=-1;
		int meta=-1;

    	if (string.contains("_")) {
    		String s[] = string.split("_");
    		if (s.length>0) id=Integer.parseInt(s[0]);
    		if (s.length>1) meta=Integer.parseInt(s[1]);
    	} else id=Integer.parseInt(string);
    	
    	return new ItemWrapper(id,meta);
	}
	
	public static int parseItemID(String string) {
		Item it = parseItem(string);
		if (it!=null) return it.itemID;
		else return -1;
	}
	
	public static Item parseItem(String string) {
		string=string.trim();
		if (isNumbers(string)) {
			int itemID = Integer.parseInt(string);
			if (itemID<Item.itemsList.length) {
				if (Item.itemsList[itemID]!=null) {
					return Item.itemsList[itemID];
				}
			}
			return null;
		}
		
		// check for alias
		ItemWrapper iw = aliasSearch(string);
		if (iw!=null) return iw.getItem();
		
		// then try translation table
		iw = translationLookup(string);
		if (iw!=null) return iw.getItem();
		
		for (int i=0; i<Item.itemsList.length; i++) {
			if (Item.itemsList[i]==null) continue;
			try {
				if (Item.itemsList[i].getUnlocalizedName()==null) continue;
				if (Item.itemsList[i].getUnlocalizedName().equals(string)) return Item.itemsList[i];
				String test = cleanName(Item.itemsList[i].getUnlocalizedName());
				if (test.equals(string)) {
					return Item.itemsList[i];
				}
			} catch (NullPointerException e) {
				XieLog.log().logDelayed("Null pointer exception for block/item id="+i+" when checking name");
				continue;
			}
		}

		return null;
	}
	
	// TODO FTFESAD strongly consider deleting
	@Deprecated
	public static int parseSide(String string) {
		BlockSide side = BlockSide.parse(string);
		if (side!=null) return side.ordinal();
		else return -4;
	}
	
	// removes the "tile." or "item." from a name if present
	public static String cleanName(String name) {
		if (name.contains("tile.")) return name.replace("tile.", "");
		if (name.contains("item.")) return name.replace("item.", "");
		return name;
	}
	
	public static List<Block> parseBlockList(String string) {
		List<Block> blocks = new LinkedList<Block>();
		String blockNames[] = string.split(",");
		for (int i=0; i<blockNames.length; i++) {
			blockNames[i]=blockNames[i].trim();
			Block thisBlock = parseBlock(blockNames[i]);
			if (thisBlock!=null) blocks.add(thisBlock);
			else {
				return null;
			}
		}
		return blocks;
	}
	
	public static Block[] parseBlockArray(String string) {
		List<Block> list = parseBlockList(string);
		if (list==null || list.isEmpty()) return null;
		
		Block array[] = new Block[list.size()];
		int i=0;
		for (Block a : list) array[i++]=a;
		return array;
	}
	
	public static Material[] parseMaterialArray(String string) {
		List<Material> list = parseMaterialList(string);
		if (list==null || list.isEmpty()) return null;
		
		Material array[] = new Material[list.size()];
		int i=0;
		for (Material a : list) array[i++]=a;
		return array;
	}
	
	public static List<Material> parseMaterialList(String string) {
		List<Material> mats = new LinkedList();
		String materials[] = string.split(",");
		for (String s : materials) mats.add(parseMaterial(s));
		return mats;
	}
	
	public static Material parseMaterial(String mat) {
		if (mat.equals("air")) return Material.air;
		else if (mat.equals("ground")) return Material.ground;
		else if (mat.equals("wood")) return Material.wood;	
		else if (mat.equals("rock")) return Material.rock;	
		else if (mat.equals("iron")) return Material.iron;	
		else if (mat.equals("water")) return Material.water;	
		else if (mat.equals("lava")) return Material.lava;	
		else if (mat.equals("leaves")) return Material.leaves;	
		else if (mat.equals("plants")) return Material.plants;	
		else if (mat.equals("vine")) return Material.vine;	
		else if (mat.equals("sponge")) return Material.sponge;	
		else if (mat.equals("cloth")) return Material.cloth;	
		else if (mat.equals("fire")) return Material.fire;	
		else if (mat.equals("sand")) return Material.sand;	
		else if (mat.equals("circuits")) return Material.circuits;	
		else if (mat.equals("glass")) return Material.glass;	
		else if (mat.equals("tnt")) return Material.tnt;
		else if (mat.equals("ice")) return Material.ice;	
		else if (mat.equals("snow")) return Material.snow;	
		else if (mat.equals("craftedSnow")) return Material.craftedSnow;	
		else if (mat.equals("cactus")) return Material.cactus;	
		else if (mat.equals("clay")) return Material.clay;	
		else if (mat.equals("pumpkin")) return Material.pumpkin;	
		else if (mat.equals("portal")) return Material.portal;	
		else if (mat.equals("cake")) return Material.cake;	
		else if (mat.equals("web")) return Material.web;	
		else if (mat.equals("piston")) return Material.piston;
		else {
			try {
				Field f = Material.class.getField(mat);
				return (Material) f.get(null);
			} catch (SecurityException e) {
				XieLog.log().logDelayed("Could not access material "+mat);
				return Material.ground; // default
			} catch (NoSuchFieldException e) {
				XieLog.log().logDelayed("Could not find material "+mat);
				return Material.ground;
			} catch (IllegalArgumentException e) {
				XieLog.log().logDelayed("Error locating material "+mat);
				return Material.ground;
			} catch (IllegalAccessException e) {
				XieLog.log().logDelayed("Error accessing material "+mat);
				return Material.ground;
			}
		}
	}
	
	public static ItemStack parseItemStack(String text) {
		// defaults
		int damage=-1;
		int amount=-1;
		String name = text.trim();
		
		if (text.contains(":")) {
			String split[] = text.split(":");
			if (split.length>2) damage=Integer.parseInt(split[2]);
			if (split.length>1) amount=Integer.parseInt(split[1]);
			if (split.length>0) name=split[0].trim();
		}
		
		// check for alias
		ItemWrapper iw = aliasSearch(name);
		if (iw!=null) {
			if (amount>0) return iw.getItemStack(amount);
			else return iw.getItemStack();
		}
		
		// then try translation table
		iw = translationLookup(name);
		if (iw!=null) {
			if (amount>0) iw.count=amount;
			if (damage>=0) iw.meta=damage;
			else return iw.getItemStack();
		}
		
		if (amount<1) amount=1;
		if (damage<0) damage=0;
		
		// check to see if name is an id
		if (isNumbers(name)) {
			int id = Integer.parseInt(name);
			if (id>0) return new ItemStack(id, amount, damage);
		}
		else {
			// search for the block or item by name
			int id = parseBlockOrItemId(name);
			if (id>0) return new ItemStack(id, amount, damage);
		}
		
		return null;
	}
	
    public static ItemWrapper aliasSearch(String string)
    {
        if(aliasMap.containsKey(string) && !(aliasMap.get(string).isEmpty())) {
        	ItemWrapper iw = aliasMap.get(string).get(0);
        	XieLog.log().finer("Hit on alias search: "+string+"=>"+iw.toString());
        	return iw;
        } else return null;
    }
	
	public static ItemWrapper translationLookup(String string) {
		string = string.toLowerCase();
		if (translationMap.containsKey(string)) {
			ItemWrapper iw = translationMap.get(string);
        	XieLog.log().finer("Hit on translation table: "+string+"=>"+iw.toString());
			return iw;
		}
		else return null;
	}
	
	public static int[] parseBlockIDs(String string) {
		List<Block> blocks = parseBlockList(string);
		if (blocks==null) return null;
		int blockIDs[] = new int[blocks.size()];
		for (int i=0; i<blocks.size(); i++) blockIDs[i]=blocks.get(i).blockID;
		return blockIDs;
	}
	
	public static Object parseBlockOrItem(String string) {
		Item it = parseItem(string);
		if (it!=null) return it;
		
		Block b = parseBlock(string);
		if (b!=null) return b;
		
		ItemStack is = parseItemStack(string);
		if (is!=null) return is;		
		
		return null;
	}
	
	public static int parseBlockOrItemId(String string) {
		Block b = parseBlock(string);
		if (b!=null) return b.blockID;

		Item it = parseItem(string);
		if (it!=null) return it.itemID;
		
		return -1;		
	}
	
	public static List parseBlocksAndItemsList(String string) {
		List list = new LinkedList();	
		String item[] = string.split(",");
		for (String s : item) {
			Object o = parseBlockOrItem(s);
			if (o!=null) list.add(o);
		}
		
		return list;
	}
	
	public static List<Integer> parseBlockIDsList(String text) {
		List<Integer> blocks = new LinkedList();
		String blockNames[] = text.split(",");
		for (int i=0; i<blockNames.length; i++) {
			blockNames[i]=blockNames[i].trim();
			
			int id;
			if (isNumbers(blockNames[i])) id = Integer.parseInt(blockNames[i]);
			else id = parseBlockID(blockNames[i]);	
			
			if (id==0 || (id>=0 && id<Block.blocksList.length && Block.blocksList[id]!=null)) {
				blocks.add(id);
			} else return null;
		}
		return blocks;
	}
	
	public static boolean itemstacksEqual(ItemStack a, ItemStack b) {
		if (a==null && b==null) return true;
		if (a==null || b==null) return false;

		if (a.itemID!=b.itemID) return false;
		if (a.stackSize!=b.stackSize) return false;
		if (a.getItemDamage()!=b.getItemDamage()) return false;
		
		return true;
	}
	
	public static Boolean isNumbers(String string) {
		for (int i=0; i<string.length(); i++) {
			if (!(string.charAt(i)=='0' || string.charAt(i)=='1'
			|| string.charAt(i)=='2' || string.charAt(i)=='3'
			|| string.charAt(i)=='4' || string.charAt(i)=='5'
			|| string.charAt(i)=='6' || string.charAt(i)=='7'
			|| string.charAt(i)=='8' || string.charAt(i)=='9')) return false;
		}
		return true;
	}
	
	public static int[] parseArray(String str) {
		String s[] = str.split(",");
		int n[] = new int[s.length];
		for (int i = 0; i<s.length; i++) {
			if (isNumbers(s[i])) n[i] = Integer.parseInt(s[i]);
			else return null;
		}
		return n;
	}
	
	private final static int defaultDuration = 1200; // one minute
	public static List<PotionEffectWrapper> parsePotionEffects(String str) {
		List<PotionEffectWrapper> list = new LinkedList();
		String sss[] = str.split(",");
		for (String ss : sss) {
			PotionEffectWrapper e = parsePotionEffect(ss);
			if (e!=null) list.add(e);
		}
		return list;
	}
	
	public static PotionEffectWrapper parsePotionEffect(String str) {
		float chance = 1;
		String s[] = str.split("@");
		if (s.length>1) {
			int c = Integer.parseInt(s[1]);
			chance = (float)c/100;
		}
		
		int duration = defaultDuration;
		int amp = 0;
		String ss[] = s[0].split(":");
		int potionID=parsePotionID(ss[0]);
		if (potionID<0) return null;
		if (ss.length>1) duration = Integer.parseInt(ss[1]) * 20;
		if (ss.length>2) amp = Integer.parseInt(ss[2])-1;
		
		return new PotionEffectWrapper(potionID,duration,amp,chance);
	}
	
	public static int parsePotionID(String s) {		
		s = potionNameTranslator(s);
		String type="potion."+s;
		for (Potion p : Potion.potionTypes) {
			if (p==null) continue;
			if (p.getName().equals(type)) return p.id;
		}
		XieLog.log().logDelayed("Couldn't identify potion effect type "+s);
		return -1;
	}
	
	public static String potionNameTranslator(String s) {
		if (s.equals("speed")) return "moveSpeed";
		
		return s;
	}
	
	public static int[] parseMetaRange(String metas) {
		metas = metas.toLowerCase();
		
		if (metas.equals("all")) return new int[]{0,15};
		else if (XieItemBucket.isValidContents(metas)) {
			int i = XieItemBucket.parseContents(metas).ordinal();
			return new int[] {i,i};
		}
		
		String s[] = metas.split(",");
		for (int i=0; i<s.length; i++) {
			if (s[i].contains("-")) {
				String ss[] = s[i].split("-");
				
				int n1 = Integer.parseInt(ss[0]);
				int n2 = Integer.parseInt(ss[1]);
				n1 = validateMeta(n1);
				n2 = validateMeta(n2);
				if (n1>n2) {
					int tmp = n2;
					n2 = n1;
					n1 = tmp;
				}
				return new int[]{n1,n2};
			} else if (s[i].contains("+")) {
				if (s[i].charAt(s.length)!='+') {
					XieLog.log().logDelayed("Invalid meta range "+s[i]+" in meta string "+metas);
					continue;
				}
				
				int n = Integer.parseInt(s[i].substring(0, s[i].length()-1));
				n = validateMeta(n);
				return new int[]{n,15};
			} else {
				int n = Integer.parseInt(s[i]);
				return new int[]{n,n};
			}
		}
		
		return null;
	}
	
	public static void setToolEffectiveness(Class toolClass, Object o, Block block) {
		Block effectiveAgainst[];
		try {
			// acquire pointer to effective against array
			//Field field = toolClass.getDeclaredField("c");
			//Field field = toolClass.getDeclaredField("blocksEffectiveAgainst");
			Field field = toolClass.getDeclaredFields()[0];
	        field.setAccessible(true);
	        effectiveAgainst = (Block[]) field.get(o);
			
			// copy array
			Block newEffectiveAgainst[] = new Block[effectiveAgainst.length+1];
			for (int i=0; i<effectiveAgainst.length; i++) 
				newEffectiveAgainst[i]=effectiveAgainst[i];
			
			// append new block
			newEffectiveAgainst[effectiveAgainst.length] = block;
			
			// reroute pointer to new array
			field.set(o, newEffectiveAgainst);
		} catch (Exception e) {
			System.out.println("Exception setting tool effectiveness for "+block.getUnlocalizedName());
			e.printStackTrace();
		}
	}
	
	public static void setPickaxeEffectiveness(Block block) {
		setToolEffectiveness(ItemTool.class,Item.pickaxeWood,block);
		setToolEffectiveness(ItemTool.class,Item.pickaxeStone,block);
		setToolEffectiveness(ItemTool.class,Item.pickaxeSteel,block);
		setToolEffectiveness(ItemTool.class,Item.pickaxeDiamond,block);
		setToolEffectiveness(ItemTool.class,Item.pickaxeGold,block);
	}
	
	public static void setAxeEffectiveness(Block block) {
		setToolEffectiveness(ItemTool.class,Item.axeWood,block);
		setToolEffectiveness(ItemTool.class,Item.axeStone,block);
		setToolEffectiveness(ItemTool.class,Item.axeSteel,block);
		setToolEffectiveness(ItemTool.class,Item.axeDiamond,block);
		setToolEffectiveness(ItemTool.class,Item.axeGold,block);
	}
	
	public static void setShovelEffectiveness(Block block) {
		setToolEffectiveness(ItemTool.class,Item.shovelWood,block);
		setToolEffectiveness(ItemTool.class,Item.shovelStone,block);
		setToolEffectiveness(ItemTool.class,Item.shovelSteel,block);
		setToolEffectiveness(ItemTool.class,Item.shovelDiamond,block);
		setToolEffectiveness(ItemTool.class,Item.shovelGold,block);
	}
	
	public static void setFlammability(int blockID, int i, int j) {
		// attempt to set burn properties via Forge's setBurnProperties method
		try {
			for (Method m : Block.class.getMethods()) {
				if (m.getName().equals("setBurnProperties")) {
					Block.setBurnProperties(blockID, i, j);
					XieLog.log().finer("FORGE Set Block Flammability for block ID "+blockID+" to "+i+","+j);
					return;
				}
			}
			
		} catch (Exception e) {
			XieLog.log().logIncomplete("Failed to set flamability @"+blockID+" ("+e+")");
			e.printStackTrace();
		}
		
		// failing that, use reflection instead
		XieLog.log().finer("Setting Block Flammability for block ID "+blockID+" to "+i+","+j+" (via reflection)");
		setFlammabilityViaReflection(blockID,i,j);	
	}
	
	// thanks to FlowerChild of the BetterThanWolves mod for this pro tip ;)
	private static boolean reflectionErrorPrinted = false;
	public static void setFlammabilityViaReflection(int blockID, int i, int j) {
		try {
			int _i[]=(int[])(ModLoader.getPrivateValue(BlockFire.class, Block.fire, 0));
			int _j[]=(int[])(ModLoader.getPrivateValue(BlockFire.class, Block.fire, 1));
			
	    	_i[blockID]=i;
	    	_j[blockID]=j;
		} catch (NullPointerException e) {
			e.printStackTrace();
			XieLog.log().logIncomplete("Failed to set flamability @"+blockID+" (NullPointerException).");
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			XieLog.log().logIncomplete("Failed to set flamability @"+blockID+" (IllegalArgumentException).");
		} catch (SecurityException e) {
			e.printStackTrace();
			XieLog.log().logIncomplete("Failed to set flamability @"+blockID+" (SecurityException).");
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
			XieLog.log().logIncomplete("Failed to set flamability @"+blockID+" (ArrayIndexOutOfBoundsException).");
		} catch (Exception e) {
			if (!reflectionErrorPrinted) {
				System.err.println("Reflection error @xie.Xie.setFlammability.");
				reflectionErrorPrinted = true;
			}
			XieLog.log().logIncomplete("Reflection error @xie.Xie.setFlammability.");
		}
	}
	
	public static String[] addToArray(String array[], String s, int index) {
		if (array==null) {
    		array=new String[index];
    	} else if (array.length<index) {
    		String newNames[] = new String[index];
    		for (int i=0; i<array.length; i++) 
    			if (array[i]!=null) newNames[i]=array[i];
    		array=newNames;
    	}
    	
    	array[index]=s;
    	
    	return array;
	}
	
	public static int parseRenderType(String s) {
		s=s.toLowerCase();
		if (s.equals("flower") || s.equals("billboard")) {
			return 1;
		} else if (s.equals("crops") || s.equals("crop")) {
			return 6;
		} else if (s.equals("sapling")) {
			return 1;
		} else if (s.equals("normal")) {
			return 0;
		} else {
			return 0;
		}
	}
	
	protected static int validateMeta(int m) {
		if (m<0) m=0;
		if (m>15) m=15;
		return m;
	}
	
	public static boolean isLogBlock(World world, int i, int j, int k) {
		int bid = world.getBlockId(i, j, k);
        return (Block.blocksList[bid] instanceof BlockLog);
	}
	public static boolean isLeafBlock(World world, int i, int j, int k) {
		int bid = world.getBlockId(i, j, k);
        return (Block.blocksList[bid] instanceof BlockLeaves);
	}
	
	public static void setDecayFlag(World world, int i, int j, int k) {
		int meta = world.getBlockMetadata(i, j, k);
        if(!isDecayFlagSet(meta)) setBlockMeta(world, i, j, k, meta | 8);
	}

	public static boolean isDecayFlagSet(int meta) {
		return (meta & 8) != 0;
	}
	
	public static void unsetDecayFlag(World world, int i, int j, int k) {
		int meta = world.getBlockMetadata(i, j, k);
        if(isDecayFlagSet(meta)) setBlockMeta(world, i, j, k, meta & -9);
	}
	
	private static final float mag = 0.7f;
	private static final float offset = (1.0F - mag) * 0.5F;
	public static void spawnItem(ItemStack is, World world, double x, double y, double z) {
		if (world.isRemote) return;
		if (is==null) return;
		float dx = world.rand.nextFloat() * mag + offset;
		float dy = world.rand.nextFloat() * mag + offset;
	    float dz = world.rand.nextFloat() * mag + offset;
	    EntityItem entityitem = new EntityItem(world, (float)x + dx, (float)y + dy, (float)z + dz, new ItemStack(is.itemID,is.stackSize,is.getItemDamage()));
	    entityitem.delayBeforeCanPickup = 10;
	    world.spawnEntityInWorld(entityitem);
	}

	public static void spawnItems(List<ItemStack> drops, World world, double x, double y, double z) {
		if (world.isRemote) return;
		if (drops==null || drops.isEmpty()) return;
		for (ItemStack is : drops) spawnItem(is, world, x, y, z);
	}
	
	public static void giveItemsToPlayer(World world, EntityPlayer player, List<ItemStack> is) {
		for (ItemStack i : is) {
			if (!player.inventory.addItemStackToInventory(i.copy()) && !world.isRemote) {
				spawnItem(i.copy(), world, player.posX, player.posY, player.posZ);
			}
		}
	}
	
	public static int entityCount(World world, int i, int j, int k, List<ItemStack> items) {
		if (items==null) return 0;
		final int d = 4;

		int count = 0;
		List<EntityItem> itemEntities = world.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(i-d,j-d,k-d,i+d,j+d,k+d));
		for (EntityItem e : itemEntities) 
			for (ItemStack is : items) {
				ItemStack it = e.getEntityItem();
				if (it!=null && it.itemID == is.itemID 
					&& it.getItemDamage() == is.getItemDamage()) count++;
			}
		return count;
	}
	
	// wrapper methods
	public static void setBlock(World world, int x, int y, int z, int id) {
		if (id<0) return;
		setBlockAndMeta(world, x, y, z, id, 0);
	}
	
	public static void setBlockAndMeta(World world, int x, int y, int z, int id, int m) {
		if (id<0) return;
		world.setBlockAndMetadataWithNotify(x, y, z, id, m, 2);
		world.markBlockForRenderUpdate(x, y, z);
	}
	
	public static void setBlockMeta(World world, int x, int y, int z, int m) {
		world.setBlockMetadataWithNotify(x, y, z, m, 2);
		world.markBlockForRenderUpdate(x, y, z);
	}
	
	public static int getBlockTexOverride(String s) {
		return XiesMod.proxy.addOverride("/terrain.png",s);
	}
	
	public static int getItemSpriteOverride(String s) {
		return XiesMod.proxy.addOverride("/gui/items.png", s);
	}
	
	public static boolean fileExists(String s) {
		File f = new File(s);
		return f.isFile();
	}
	
    public static void addSmeltingRecipe(int i, ItemStack itemstack) {
    	ModLoader.addSmelting(i,itemstack);
    }
	
    public static void addSmeltingRecipe(int i, ItemWrapper item) {
    	ModLoader.addSmelting(i,item.getItemStack());
    }
    
    public static void addFuel(int id, int meta, int burnTime)  {
        XieFuelManager.add(new ItemWrapper(id,meta),burnTime);
    }

    public static void addFuel(int id, int burnTime) {
    	XieFuelManager.add(new ItemWrapper(id), burnTime);
    }
    
    public static int getFuel(int id, int meta) {
    	return XieFuelManager.getItemBurnTime(new ItemStack(id, 1, meta));
    }
    
    @Deprecated
	public static String textureWrapper(IXieBlock block) {
		return "";
	}
	
	public static String textureWrapper(XieItemInterface item) {
		if (item.getTexFile()==null) return "/gui/items.png";
		else return item.getTexFile();
	}

	public static boolean isTransparentRenderType(int r) {
		return r!=0;
	}

	public static int getUniqueSpriteIndex(String string) {
		return ModLoader.getUniqueSpriteIndex(string);
	}

	public static RenderEngine getRenderEngine() {
		return ModLoader.getMinecraftInstance().renderEngine;
	}

	public static void registerTileEntity(Class c, String name, TileEntitySpecialRenderer renderer) {
		ModLoader.registerTileEntity(c, name, renderer);
	}

//	@SideOnly(Side.CLIENT)
//	public static void registerTexture(int index, int i, BufferedImage image) {
//		ModTextureStatic texture = new ModTextureStatic(index, i, image);
//      	Xie.getRenderEngine().registerTextureFX(texture);
//	}

	public static void playBreakSoundEffect(World world, Block block, float i, float j, float k) {
		XiesMod.proxy.playSoundEffect(world, i + 0.5F, j + 0.5F, k + 0.5F, 
				block.stepSound.getBreakSound(), (block.stepSound.getVolume() + 1.0F) / 2.0F, 
				block.stepSound.getPitch() * 0.8F);
	}

	public static void playBlockCrunch(World world, int i, int j, int k, int bid) {
		if (bid<0 || bid>=Block.blocksList.length);
		Block block = Block.blocksList[bid];
		XiesMod.proxy.playSoundEffect(world, i + 0.5F, j + 0.5F, k + 0.5F, block.stepSound.getStepSound(), (block.stepSound.getVolume() + 1.0F) / 2.0F, block.stepSound.getPitch() * 0.8F);
	}

	public static void playSoundEffect(World world, int x, int y, int z, int id, int n) {
		XiesMod.proxy.playAuxSFX(world, x, y, z, id, n);
	}
	
	public static String[] parseNames(String str) {
		String s[] = str.split(",");
		for (int i=0; i<s.length; i++) s[i] = s[i].trim();
		return s;
	}
	
	public static void addBonemealable(XieBlockPlant plant) {
		BonemealHandler.add(plant);
	}

	public static void addDungeonLoot(DungeonLootHelper l) {
		DungeonHooks.addDungeonLoot(l.itemstack, l.rarity, l.min, l.max);
	}

	public static void addBlockDestroyEffects(int i, int j, int k, int bid, int l) {
		XiesMod.proxy.addBlockDestroyEffects(i,j,k,bid,l);
	}

	public static int getFacing(Entity e) {
		return MathHelper.floor_double((double)(e.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
	}

	public static boolean validateImageFile(String textureFilePath) {
		return XiesMod.proxy.validateImageFile(textureFilePath);
	}

	public static EnumPlantType parsePlantType(String str) {
		for (EnumPlantType t : EnumPlantType.values()) {
			if (t.name().equalsIgnoreCase(str)) return t;
		}
		
		return null;
	}

	public static ForgeDirection getRandomDir(Random r) {
		return ForgeDirection.VALID_DIRECTIONS[r.nextInt(ForgeDirection.VALID_DIRECTIONS.length)];
	}
}
