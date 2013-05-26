package mod.xie.xm.parser;

import java.util.HashMap;

import mod.xie.xm.Work;
import mod.xie.xm.file.SpritesheetManager;
import mod.xie.xm.items.ArmorEffects;
import mod.xie.xm.items.XieItem;
import mod.xie.xm.items.XieItemArmor;
import mod.xie.xm.items.XieItemBow;
import mod.xie.xm.items.XieItemBucket;
import mod.xie.xm.items.XieItemFertiliser;
import mod.xie.xm.items.XieItemFood;
import mod.xie.xm.items.XieItemInterface;
import mod.xie.xm.items.XieItemSeeds;
import mod.xie.xm.items.XieItemSet;
import mod.xie.xm.items.XieItemStick;
import mod.xie.xm.items.XieItemSword;
import mod.xie.xm.items.XieItemTool;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.tex.XieTexLink;
import mod.xie.xm.util.BlockID;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.JobType;
import mod.xie.xm.util.PotionEffectWrapper;
import mod.xie.xm.util.Recipe;
import mod.xie.xm.util.Xie;
import mod.xie.xm.util.XieMaterialManager;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.item.EnumArmorMaterial;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;


import net.minecraft.src.ModLoader;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;
import argo.jdom.JsonStringNode;
import net.minecraftforge.common.DungeonHooks;
import net.minecraftforge.common.EnumHelper;

public class JobItem extends Job  {

	private static final int FAIL_ICON = 15;
	
	protected XieItemInterface item;
	protected int id=-1;
	
	public JobItem(String n, JsonNode node, String path) {
		super(JobType.ITEM, n, node);
		this.path=path;
	}
	
	@Override
	public int doWork() {
		logOpen();
		// validate name
		if (!isKey("name")) {
			logFailure("No name");
			return 0;
		}
		
		if (id<0) {
			id = getID();
			if (id<=0) {
				logFailure("FAILED to find id, job undoable");
				return 0;
			} else if (id>=Item.itemsList.length) {
				logFailure("Invalid ID "+id+", exceeds item index length "+Item.itemsList.length+", job undoable.");
				return 0;
			} else if (Item.itemsList[id]!=null && item==null) {
				if (isKey("overide") || isKey("override")) {
					logInfo("Item id "+id+" is in use. Overriding "+Item.itemsList[id].getUnlocalizedName());
//					if (!isKey("icon")) copyIconsFrom(Item.itemsList[id]);
					Item.itemsList[id]=null;
				} else {
					logFailure("ID "+id+": is in use (unshifted id="+(id-256)+"). Use a different ID or use 'override' to replace existing block or item.");
					return 0;
				}
				
			}
		}
				
		String subtype="basic"; // default
		if (isKey("type")) subtype = getStr("type").toLowerCase();
		
		if (subtype.equals("food")) doFood();
		else if (subtype.equals("seeds") || subtype.equals("seed")) doSeeds(); 
		else if (subtype.equals("fertiliser") || subtype.equals("fertilizer")) isDone=doFertiliser(); 
		else if(subtype.equals("stick")) doStick(); 
		else if (subtype.equals("tool")) doTool(); 
		else if (subtype.equals("sword")) doSword();
		else if (subtype.equals("bow")) doBow();
		else if (subtype.equals("bucket")) doBucket();
		else if (subtype.equals("helm")) doArmor(0);
		else if (subtype.equals("chestplate")) doArmor(1);
		else if (subtype.equals("leggings")) doArmor(2);
		else if (subtype.equals("boots")) doArmor(3);
		else if (subtype.equals("armor")) doArmor(-1);
		else doBasic();
		
		if (item==null) return 0;
		
		// do a few final properties
		if (isKey("texFile")) doTexFile();
		
		if (isKey("cooked") || isKey("smelted")) isDone = parseSmeltingRecipe();

		if (isKey("fuel")) expandFuelNode(id);
		
		if (isKey("dungeonLoot")) doDungeonLoot(getNode("dungeonLoot"));
		
		if (isKey("names")) {
			String names[] = expandNamesNode(getNode("names"));
			item.setNames(names);
			String namess = "";
			for (String n : names) namess+=n+",";
			namess=namess.substring(0,namess.length()-1);
			logFiner("Set names: "+namess);
			for (int i=0; i<names.length; i++) 
				ModLoader.addName(new ItemStack(item.getItem().itemID,1,i), names[i]);
			item.setHasSubtypes(true);
		}
		
		if (isKey("drops")) {
			Droppables drops = expandDropsNode(getNode("drops"));
			if (drops!=null && !drops.isEmpty()) {
				item.setDrops(drops);
				logFiner("Setting drops: "+drops);
			} else isDone=false;
		}
		
		if (isKey("stacksize")) 
			item.setMaxStackSize(getInt("stacksize"));
		
		if (isKey("container")) {
			Item it = Xie.parseItem(getStr("container"));
			if (it!=null) {
				//item.getItem().setMaxStackSize(1);
				item.getItem().setContainerItem(it);
				if (item.getDrops()==null || item.getDrops().isEmpty())
					item.setDrops(new Droppables(new Drop(it)));
			}
			else {
				logDelayed("Failed to parse container item: "+getStr("container"));
				isDone=false;
			}
		}
		
		if (isKey("toolMaterial")) {
			doToolMaterial();
		}
		
		if (isKey("armorMaterial")) {
			doArmorMaterial();
		}
		
		if (subtype.equals("armor") && isDone) return 4;
		
		if (isDone) finaliseItem();
		else return 0;
		
		return 1;
	}
	
	protected void doTexFile() {
		String spritesheet = getStr("texFile");
		
		if (spritesheet!=null) {
			if (spritesheet.startsWith("http://")) spritesheet = loadURLSpritesheet(spritesheet);
			spritesheet=formatTextureFilepath(spritesheet);
			item.setTexFile(spritesheet);
		}
	}
	
	protected void doFood() {
		logFiner("Processing food item");
		// food type!
		if (item==null) {
			int hunger = 2;
			int thirst = 0;
			
			if (isKey("hunger")) hunger = getInt(getNode("hunger"));
			if (isKey("thirst")) thirst = getInt(getNode("thirst")); 
			
			float nourishment = 0.6F;
			if (isKey("nourishment")) {
				nourishment = getFloat(getNode("nourishment"));
				item = new XieItemFood(name, id, hunger, nourishment, isDogFood());
				((XieItemFood)item).setThirstAmount(thirst);
			} else item = new XieItemFood(name, id, hunger, isDogFood());
			logFiner("Added food: "+name+"("+id+") hunger: "+hunger+" nourishment: "+nourishment);
			
			if (isKey("action")) ((XieItemFood)item).setAction(getStr("action"));
			
			if (isKey("potionEffect") || isKey("potionEffects")) {
				JsonNode en;
				if (isKey("potionEffect")) en=getNode("potionEffect");
				else en=getNode("potionEffects");
				
				if (en.getType()==JsonNodeType.STRING) {
					List<PotionEffectWrapper> l = Xie.parsePotionEffects(en.getText());
					if (l!=null) ((XieItemFood)item).setEffects(l);
					else logIncomplete("Failed to parse potion effects string");
				} else if (en.getType()==JsonNodeType.OBJECT) {
					Map<JsonStringNode, JsonNode> map = en.getFields();
					for (JsonNode key : map.keySet()) {
						int id;
						if (key.getText().trim().toLowerCase().equals("all")) id = -1;
						else id=Integer.parseInt(key.getText());
						
						List<PotionEffectWrapper> l = Xie.parsePotionEffects(map.get(key).getText());
						if (l!=null) {
							((XieItemFood)item).setEffects(id,l);
							logFiner("Setting potion effects "+l);
						}
						else logIncomplete("Failed to parse effects for meta "+key.getText());
					}
					
				} else {
					logIncomplete("Invalid potionEffect node type");
				}
			}
		}
		isDone = true;
	}
	
	protected void doSeeds() {
		logFiner("Processing seed item");
		Block growsInto = Xie.parseBlock(getStr("growsInto"));
		if (growsInto!=null) {
			XieItemSeeds seeds;
			if (item==null) seeds = new XieItemSeeds(id, growsInto, name);
			else seeds = (XieItemSeeds) item;
			if (isKey("inheritMeta")) seeds.setGrowsIntoMeta(-1);
			if (isKey("growsIntoMeta")) seeds.setGrowsIntoMeta(getInt(getNode("growsIntoMeta")));
			item = seeds;
			logFiner("Added seeds: "+name+"("+id+") grows "+growsInto.getUnlocalizedName());
			isDone = true;
		} else {
			logDelayed("Failed to identify 'growsInto' block");
			return;
		}
	}
	
	protected void doBucket() {
		logFiner("Processing bucket item");
		
		XieItemBucket bucket;
		if (item==null) bucket = new XieItemBucket(name, id);
		else bucket = (XieItemBucket) item;

		if (isKey("canHoldWater")) bucket.setCanHold(XieItemBucket.Contents.water,getBoolean("canHoldWater"));
		if (isKey("canHoldLava")) bucket.setCanHold(XieItemBucket.Contents.lava,getBoolean("canHoldLava"));
		if (isKey("canHoldMilk")) bucket.setCanHold(XieItemBucket.Contents.milk,getBoolean("canHoldMilk"));
		
		if (isKey("bucketNames")) {
			Map<JsonStringNode, JsonNode> map = getNode("bucketNames").getFields();
			for (JsonNode key : map.keySet()) bucket.setName(key.getText(), map.get(key).getText());
		}
		
		item = bucket;
		logFiner("Added bucket");
		isDone = true;
	}

	protected boolean doFertiliser() {
		logFiner("Processing fertiliser item");
		boolean defaultFertiliser = true;
		
		if (isKey("worksOn")) {
			defaultFertiliser=false;
			List<Block> l = Xie.parseBlockList(getStr("worksOn"));
			if (l==null || l.isEmpty()) {
				logDelayed("Failed to parse worksOn block list");
				return false;
			} else {
				String worksOnList = " which works on ";
				for (Block b : l) worksOnList+=b.getUnlocalizedName();
				logFiner("Added fertiliser "+name+"("+id+") "+worksOnList);
				if (item==null) item = new XieItemFertiliser(name, id, l);
			}
		}
		
		if (defaultFertiliser) {
			logFiner("Added fertiliser: "+name+"("+id+")");
			item = new XieItemFertiliser(name, id);
		}
		
		return true;
	}
	
	protected void doStick() {
		logFiner("Processing stick item");
		XieItemStick stick;
		if (item==null) {
			stick = new XieItemStick(name,id);
		} else {
			stick = (XieItemStick)item;
		}
		if(isKey("blocks")) {
			// expand affected blocks node
			problemFlag = false;
			Map<JsonStringNode, JsonNode> map = getNode("blocks").getFields();
			for (JsonNode key : map.keySet()) {
				BlockID b = Xie.parseBlockAndMetadata(key.getText());
				if (b==null) {
					logDelayed("Failed to identify block "+key.getText());
					item = stick;
					return;
				}
				Droppables drops = expandDropsNode(map.get(key));
				if (drops==null || problemFlag) {
					logDelayed("Failed to expand drops node");
					problemFlag = true;
				} else stick.addDrops(b, drops);
			}
		}
		if(isKey("destroyChance")) {
			stick.setDestroyChance(getInt(getNode("destroyChance")));
		}
		if(isKey("uses")) {
			stick.setMaxDamage(getInt(getNode("uses")));
		}
		
		item=stick;
		logFiner("Added stick: "+name+"("+id+")");
		if (problemFlag) {
			isDone=false;
			logFiner("Job incomplete, some drops remain unresolved");
		} else isDone=true;
	}

	// TODO add "toolSet" type
	protected void doTool() {
		logFiner("Processing tool item");
		EnumToolMaterial toolMaterial = getToolMaterial();
		if (toolMaterial == null) {
			logDelayed("Could not find tool's material");
			isDone = false;
			return;
		}
		
		if (item==null) { 
			item = new XieItemTool(name, id, getToolDamage(), toolMaterial);
			logFiner("Initialised tool object, "+name+" id: "+id);
		}
		
		isDone=true;
		
		if (isKey("blocksEffective")) {
			String s = getNode("blocksEffective").getText();
			if (s.trim().toLowerCase().equals("all")) ((XieItemTool)item).makeUltimate();
			else {
				Block arr[] = Xie.parseBlockArray(s);
				if (arr==null) isDone=false;
				else ((XieItemTool)item).setBlockEffectivity(arr);
			}
		}
		
		if (isKey("magicWand")) ((XieItemTool)item).makeMagic();
		
		if (isKey("materialsEffective")) {
			String s = getNode("materialsEffective").getText();
			Material arr[] = Xie.parseMaterialArray(s);
			if (arr==null) isDone=false;
			else ((XieItemTool)item).setMaterialEffectivity(arr);
		}
		
		if (isKey("canHarvest")) {
			Block bs[] = expandHarvestNode("canHarvest");
			if (bs!=null) {
				((XieItemTool)item).setHarvestables(bs,true);
			}
		}
		
		if (isKey("cannotHarvest")) {
			Block bs[] = expandHarvestNode("cannotHarvest");
			if (bs!=null) {
				((XieItemTool)item).setHarvestables(bs,false);
			}
		}
		
		if (isKey("transforms")) expandTransformsNode();
		
		if (isKey("efficiency")) ((XieItemTool)item).setEfficiency(getFloat("efficiency"));
	}
	
	protected void doSword() {
		logFiner("Processing sword item");
		EnumToolMaterial toolMaterial = getToolMaterial();
		if (toolMaterial == null) {
			logDelayed("Could not find sword's material");
			isDone = false;
			return;
		}
		
		if (item==null) { 
			item = new XieItemSword(name, id, toolMaterial);
			logFiner("Initialised sword object, "+name+" id: "+id);
		}
		
		isDone=true;
		
		if (isKey("canCut")) {
			String s = getNode("canCut").getText();
			
			Block arr[] = Xie.parseBlockArray(s);
			if (arr==null) {
				logDelayed("Failed to parse 'canCut' block array");
				isDone=false;
			} else ((XieItemSword)item).setCanCut(arr);
		}
		
		if (isKey("damage")) ((XieItemSword)item).setWeaponDamage(getInt("damage"));
		if (isKey("uses")) ((XieItemSword)item).setNumberOfUses(getInt("uses"));
		if (isKey("flame")) ((XieItemSword)item).setFlame(getInt("flame"));
		if (isKey("canBlock")) ((XieItemSword)item).setCanBlock(getBoolean("canBlock"));
		if (isKey("enchantability")) ((XieItemSword)item).setEnchantability(getInt("enchantability"));
		
	}
	
	protected void doBow() {
		logFiner("Processing bow item");
		if (item==null) { 
			item = new XieItemBow(name, id);
			logFiner("Initialised bow object, "+name+" id: "+id);
		}
		
		isDone=true;
		
		if (isKey("ammo")) {
			String s = getNode("ammo").getText();
			
			List<ItemWrapper> list = Xie.parseItemWrappers(s);
			if (list==null || list.isEmpty()) {
				logDelayed("Failed to parse ammo types");
				isDone=false;
			} else ((XieItemBow)item).setAmmunition(list);
		}
		
		if (isKey("power")) ((XieItemBow)item).setPower(getInt("power"));
		if (isKey("punch")) ((XieItemBow)item).setPunch(getInt("punch"));
		if (isKey("flame")) ((XieItemBow)item).setFlame(getInt("flame"));
		if (isKey("uses")) ((XieItemBow)item).setNumberOfUses(getInt("uses"));
		if (isKey("enchantability")) ((XieItemBow)item).setEnchantability(getInt("enchantability"));
		if (isKey("ammoless")) ((XieItemBow)item).setAmmoless(getBoolean("ammoless"));
		if (isKey("semiautomatic")) ((XieItemBow)item).setSemiautomatic(getBoolean("semiautomatic"));
		if (isKey("ammoConsumed"))  ((XieItemBow)item).setAmmoConsumed(getBoolean("ammoConsumed"));
		
	}
	
	protected void doArmor(int armorType) {
		if (armorType<0) logFiner("Processing armor set");
		else logFiner("Processing armor item");
		
		EnumArmorMaterial material;
		if (isKey("material")) {
			material = XieMaterialManager.instance().getArmorMaterial(getStr("material"));
			if (material == null) {
				logDelayed("Could not find armor material");
				isDone=false;
				return;
			}
		} else {
			int maxDamage = isKey("maxDamage")?getInt("maxDamage"):5;
			int dmgReduction[] = new int[] {1, 3, 2, 1};
			if (isKey("damageReduction")) {
				int dr[] = Xie.parseArray(getStr("damageReduction"));
				if (dr.length<4) {
					logIncomplete("Incomplete damage reduction array, found only "+dr.length+"/4 elements");
					for (int i=0; i<dr.length; i++) dmgReduction[i]=dr[i];
				} else {
					for (int i=0; i<4; i++) dmgReduction[i]=dr[i];
				}
			}
			
			int enchantability = isKey("enchantability")?getInt("enchantability"):15;
			Item craftingItem = null;
			if (isKey("craftedFrom")) craftingItem = Xie.parseItem(getStr("craftedFrom"));
			material = XieMaterialManager.instance().newArmorMaterial(name, maxDamage, dmgReduction, enchantability,craftingItem);
		}
		
		String armorTextures[] = getArmorTextures();
		String armorTexture = armorTextures[0];
		String armorTextureBottom = armorTextures[1];
		
		ItemStack ingredient = null;
		if (isKey("craftedFrom")) {
			ingredient = Xie.parseItemStack(getStr("craftedFrom"));
			if (ingredient==null) {
				logDelayed("Could not identify crafting ingredient, "+getStr("craftedFrom"));
				return;
			}
		}
		
		ArmorEffects properties = parseArmorEffects();
		
		if (armorType<0) {
			String names[] = getSetNames(new String[] {"Helm", "Chestplate", "Leggings", "Boots"});
			int ids[] = getSetIds(4);
			
			XieItemSet itemSet = new XieItemSet(name);
			for (int i=0; i<4; i++) {
				XieItemArmor armor = doArmor(name,names[i],ids[i],material,i,
						i!=2?armorTexture:armorTextureBottom,ingredient);
				
				armor.setProperties(properties);
				itemSet.add(armor);
				Work.instance().itemAdded(ids[i]);
			}
			item = itemSet;
			
			isDone=true;
			logInfo(" ===  Item Job done! === ");

		} else {
			if (item==null) {
				XieItemArmor armor = new XieItemArmor(name, id, material,armorType);
				if (ingredient!=null) {
					addArmorRecipe(armorType,ingredient,armor);
				}
				armor.setArmorTextureFile(armorTexture);
				armor.setProperties(properties);
				item = armor;
				logFiner("Initialised armor object, "+name+" id: "+id);
			}
			
			isDone=true;
		}
	}
	
	protected ArmorEffects parseArmorEffects() {
		ArmorEffects a = new ArmorEffects();
		
		if (isKey("resistance")) {
			Map<JsonStringNode,JsonNode> map = getNode("resistance").getFields();
			for (Entry<JsonStringNode,JsonNode> e : map.entrySet()) {
				String damageType = e.getKey().getText();
				int amt = getInt(e.getValue());
				a.addResistance(damageType, amt);
			}
		}
		
		return a;
	}
	
	protected String[] getArmorTextures() {
		String armorTexture = null;
		String armorTextureBottom = null;
		
		if (isKey("armorTexture")) armorTexture=getStr("armorTexture");
		if (isKey("armorTexture1")) armorTexture=getStr("armorTexture1");
		
		if (isKey("armorTextureLegs")) armorTextureBottom=getStr("armorTextureLegs");
		if (isKey("armorTexture2")) armorTextureBottom=getStr("armorTexture2");
		if (isKey("armorTextures")) {
			String texes[] = Xie.parseNames(getStr("armorTextures"));
			if (texes.length!=2) logIncomplete("Expected 2 armor textures, not "+texes.length);
			armorTexture=texes[0];
			armorTextureBottom=texes[1];
		}
		
		SpritesheetManager.get().handleArmourTextures(fullname(), path, armorTexture, armorTextureBottom);
		
		return new String[]{armorTexture,armorTextureBottom};
	}
	
	protected XieItemArmor doArmor(String basename, String fullname, int id, EnumArmorMaterial mat, int type, String tex, ItemStack ingredient) {
		String name = basename+"."+XieItemArmor.SUFFIXES[type];
		XieItemArmor armor = new XieItemArmor(name, id, mat, type);
		armor.setArmorTextureFile(tex);
		ModLoader.addName(armor, fullname);
		if (ingredient!=null) {
			addArmorRecipe(type,ingredient,armor);
		}
		logFiner("Initialised "+name+" object, id: "+id);
		return armor;
	}
	
	protected void addArmorRecipe(int type, ItemStack ingredient, XieItemArmor armor) {
		Recipe.makeArmorRecipe(type, new ItemWrapper(armor), ingredient);
		logFiner("Added recipe for "+name);
		Work.instance().jobsCompleted[JobType.RECIPE.ordinal()]++;
	}
	
	protected void doToolMaterial() {
		JsonNode node = getNode("toolMaterial");
		if (node.getType()!=JsonNodeType.OBJECT) {
			logIncomplete("Invalid toolMaterial node type");
		}
		
		Map<JsonStringNode, JsonNode> map = node.getFields();
		EnumToolMaterial base = null;
		if (map.containsKey("base")) {
			base = XieMaterialManager.instance().getToolMaterial(map.get("base").getText());
		}
		for (JsonStringNode key : map.keySet()) {
			if (key.getText().equals("harvestLevel")) {
				// TODO FTFESAD
			}
		}
	}
	
	protected void doArmorMaterial() {
		// TODO FTFESAD
	}
	
	protected void doBasic() { 	
		if (item==null) {
			logFiner("Added basic item: "+name+"("+id+")");
			item = new XieItem(name, id);
		}
		// Do some other stuff?
		isDone = true;
	}
	
	protected void expandTransformsNode() {
		JsonNode node = getNode("transforms");
		if (node.getType()==JsonNodeType.STRING) {
			int id = Xie.parseBlockID(node.getText());
			if (id>=0) ((XieItemTool)item).transforms.put(new BlockID(),new BlockID(id));
		} else if (node.getType()==JsonNodeType.OBJECT) {
			Map<JsonStringNode, JsonNode> map = node.getFields();
			for (JsonNode key : map.keySet()) {
				JsonNode val = map.get(key);
				if (val.getType()!=JsonNodeType.STRING) {
					logIncomplete("Invalid type in transforms node for key "+key.getText());
					continue;
				}
				
				BlockID blockFrom;
				if (key.getText().equals("all")) blockFrom=new BlockID();
				else {
					blockFrom = Xie.parseBlockAndMetadata(key.getText());
					if (!blockFrom.isBlock()) {
						logDelayed("Could not identify block "+key.getText()+" in transforms node");
						continue;
					}
				}
				
				BlockID blockTo = Xie.parseBlockAndMetadata(val.getText());
				if (!blockTo.isBlock()) {
					logDelayed("Could not identify block "+val.getText()+" in transforms node");
					continue;
				}
				
				((XieItemTool)item).transforms.put(blockFrom,blockTo);
			}
		}
	}
	
	protected Block[] expandHarvestNode(String s) {
		JsonNode node = getNode(s);
		if (node.getType()==JsonNodeType.STRING) {
			Block b[] = Xie.parseBlockArray(node.getText());
			if (b==null) {
				logDelayed("Could not parse block list for canHarvest node");
				return null;
			}
			return b;
		} else {
			logIncomplete("Invalid canHarvest node type");
			return null;
		}
	}
	
	protected boolean isDogFood() {
		if (isKey("dogFood")) return getBoolean(getNode("dogFood"));
		else return false;
	}
	
	protected void finaliseItem() {
		ModLoader.addName(item, getStr("name"));
		Work.instance().itemAdded(id);
		logInfo(" ===  Item Job done! === ");
	}
	
	// TODO add support for meta-data sensitive JSON object
	protected boolean parseSmeltingRecipe() {
		if (item==null) return false;
		
		String cooked;
		if (isKey("cooked")) cooked = getStr("cooked");
		else cooked = getStr("smelted");
		
		ItemWrapper cookedStack = Xie.parseItemWrapper(cooked);
		if (cookedStack!=null) {
			logFiner("Cooks/smelts into "+cookedStack.getUnlocalizedName());
			Xie.addSmeltingRecipe(id, cookedStack);
			if (cookedStack.getItem() instanceof ItemFood) {
				foodSmeltsSoFar.put(new ItemWrapper(id),cookedStack);
				for (Map<ItemWrapper,ItemWrapper> map : foodSmeltMapsToUpdate) map.put(new ItemWrapper(id),cookedStack);
			}
			return true;
		} else {
			logDelayed("Couldn't parse cook/smelt recipe "+cooked);
			return false;
		}
	}
	
	// TODO FTFESAD
//	protected XieTex expandIconNode(JsonNode iconNode) {
//		if (iconNode.getType()==JsonNodeType.STRING) {
//			String iconText = iconNode.getText();
//			if (iconText.startsWith("#")) {
//				// steal icon(s) from another item
//				Item it = Xie.parseItem(iconText.substring(1));
//				if (it!=null) {
//					return new XieTexLink(it);
//				}
//				else {
//					logDelayed("Failed to find reference item "+iconText);
//					isDone=false;
//					return null;
//				}
//			}
//		}
//	}
	
	protected int getToolDamage() {
		if (isKey("damage")) return getInt("damage");
		else return 1;
	}
	
	protected EnumToolMaterial getToolMaterial() {
		if (isKey("material")) {
			return XieMaterialManager.instance().getToolMaterial(getStr("material"));
		} else return EnumToolMaterial.WOOD;
	}
	
	@Deprecated
	protected EnumToolMaterial getToolMaterial(String s) {
		s=s.trim().toUpperCase();
		if (s.equals("WOOD")) return EnumToolMaterial.WOOD;
		else if (s.equals("STONE")) return EnumToolMaterial.STONE;
		else if (s.equals("IRON")) return EnumToolMaterial.IRON;
		else if (s.equals("GOLD")) return EnumToolMaterial.GOLD;
		else if (s.equals("EMERALD")) return EnumToolMaterial.EMERALD;
		else if (s.equals("DIAMOND")) return EnumToolMaterial.EMERALD;
		else {
			logIncomplete("Failed to parse tool material "+s+", defaulting to wood");
			return EnumToolMaterial.WOOD;
		}
	}

	protected String getDefaultTextureFile() {
		return "/gui/items.png";
	}
	
	protected String[] getSetNames(String [] suffixes) {
		int n = suffixes.length;
		String names [] = null;
		if (isKey("names")) {
			names = Xie.parseNames(getStr("names"));
			if (names.length!=n) {
				logIncomplete("Invalid names, required "+n);
				names=null;
			}
		}

		String baseName = getStr("name");
		if (names==null) {
			names = new String[n];
			for (int i=0; i<n; i++) names[i] = baseName+" "+suffixes[i];
		}
		
		return names;
	}
	
	protected int[] getSetIds(int n) {
		int ids[] = null;
		if (isKey("ids")) {
			ids = Xie.parseArray(getStr("ids"));
			if (ids.length!=n) {
				ids=null;
				logIncomplete("incomplete ids array, require "+n);
			}
		}
		
		if (ids==null) {
			ids = new int[n];
			for (int i=0; i<n; i++) ids[i]=id+i;
		}
		
		return ids;
	}
	
	protected void doDungeonLoot(JsonNode j) {
		String s = j.getText();
		
		float rarity=1F;
		if (s.contains("@")) {
			String ss[] = s.split("@");
			rarity = Float.parseFloat(ss[1]);
			s=ss[0];
		}
		
		int min=1;
		if (s.contains("-")) {
			String ss[] = s.split("-");
			min = Integer.parseInt(ss[0]);
			s=ss[1];
		}
		int max = Integer.parseInt(s);
		
		Xie.addDungeonLoot(new DungeonLootHelper(new ItemStack(item.getItem()), rarity, min, max));
	}
}
