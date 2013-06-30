package mod.xie.xm.parser;

import java.util.HashMap;

import mod.xie.xm.Work;
import mod.xie.xm.blocks.IXieBlock;
import mod.xie.xm.blocks.XieBlock;
import mod.xie.xm.blocks.XieBlockBush;
import mod.xie.xm.blocks.XieBlockChest;
import mod.xie.xm.blocks.XieBlockCrop;
import mod.xie.xm.blocks.XieBlockFacing;
import mod.xie.xm.blocks.XieBlockFence;
import mod.xie.xm.blocks.XieBlockFurnace;
import mod.xie.xm.blocks.XieBlockGlass;
import mod.xie.xm.blocks.XieBlockGrass;
import mod.xie.xm.blocks.XieBlockLeaves;
import mod.xie.xm.blocks.XieBlockLog;
import mod.xie.xm.blocks.XieBlockMounted;
import mod.xie.xm.blocks.XieBlockPane;
import mod.xie.xm.blocks.XieBlockPlant;
import mod.xie.xm.blocks.XieBlockSapling;
import mod.xie.xm.blocks.XieBlockStem;
import mod.xie.xm.items.XieItemBlock;
import mod.xie.xm.items.XieItemTool;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.BlockID;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.JobType;
import mod.xie.xm.util.Xie;
import mod.xie.xm.world.TreeGenerator;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.registry.GameRegistry;



import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.src.ModLoader;
import net.minecraftforge.common.DungeonHooks;
import net.minecraftforge.common.EnumPlantType;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;
import argo.jdom.JsonStringNode;

public class JobBlock extends Job {

	protected IXieBlock block;
	protected int id = -1;
	
	public JobBlock(String n, JsonNode node, String path) {
		super(JobType.BLOCK, n, node);
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
		
		if (id<=0) id = getID();
		
		if (id<=0) {
			logFailure("FAILED to find id, job undoable");
			return 0;
		} else if (id>=Block.blocksList.length) {
			logFailure("Invalid ID "+id+", exceeds block index length "+Block.blocksList.length+", job undoable.");
			return 0;
		} else if (Block.blocksList[id]!=null && block==null) {
			if (isKey("overide") || isKey("override")) {
				logInfo("Block id "+id+" is in use. Overriding block '"+Block.blocksList[id].getUnlocalizedName()+"'.");
				Block.blocksList[id]=null;
				Item.itemsList[id]=null;
			} else {
				logFailure("ID "+id+": is in use. Use a different ID or use 'override' to replace existing block or item.");
				return 0;
			}
		}
		
		String subtype = "basic"; // default
		if (isKey("type")) subtype = getStr("type");
		
		if (subtype.equals("plant")) {
			logFiner("Init plant block with id="+id+", name="+name);
			XieBlockPlant plant;
			if (block==null) {
				plant = new XieBlockPlant(name, id);
			}
			else plant = (XieBlockPlant) block;
			Boolean complete = setPlantProps(plant);
			block = plant;
			
			if (!complete) {
				logClose();
				return 0;
			}
			isDone = expandCommonBlockParameters();
		} else if (subtype.equals("crop")) {
			logFiner("Init crop block with id="+id+", name="+name);
			XieBlockCrop crop;
			if (block==null) {
				crop = new XieBlockCrop(name, id);
			}
			else crop = (XieBlockCrop) block;	
			Boolean complete = setPlantProps(crop);
			block = crop;
			
			if (!complete) {
				logClose();
				return 0;
			}
			isDone = expandCommonBlockParameters();
		} else if (subtype.equals("stem")) { 
			if (!doStem(id)) return 0;
		} else if (subtype.equals("grass")) {
			if (!doGrass(id)) return 0;
		} else if (subtype.equals("chest")) {
			if (!doChest(id)) return 0;
		} else if (subtype.equals("bush")) {
			logFiner("Init bush block: "+name+"("+id+")");
			XieBlockBush bush;
			if (block==null) {
				bush = new XieBlockBush(name, id);
			}
			else bush = (XieBlockBush) block;
			Boolean complete = setPlantProps(bush);
			block=bush;
			
			if (!complete) {
				logClose();
				return 0;
			}
			isDone = expandCommonBlockParameters();
		} else if (subtype.equals("tree")) {
			if (!doTree(id)) return 0;
		} else if (subtype.equals("log")) {
			logFiner("Init log block: "+name+"("+id+")");
			XieBlockLog log;
			if (block==null) {
				log = new XieBlockLog(name, id);
			} else log = (XieBlockLog)block;
			
			block = log;
			
			block.setStepSound(Block.soundWoodFootstep);
			isDone = expandCommonBlockParameters();
		} else if (subtype.equals("sapling")) {
			if (!doSapling(id)) return 0;
		} else if (subtype.equals("furnace")) {
			if (!doFurnace(id)) return 0;
		} else if (subtype.equals("mounted")) {
			if(!doMounted(id)) return 0;
		} else if (subtype.equals("facing")) {
			if(!doFacing(id)) return 0;
		} else if (subtype.equals("pane")) {
			logFiner("Init window pane with id="+id+", name="+name);
			XieBlockPane b;
			if (block==null) {
				b = new XieBlockPane(name, id, getMaterial());
			} else b=(XieBlockPane)block;
			
			block = b;
			
			isDone = expandCommonBlockParameters();
		} else if (subtype.equals("fence")) {
			logFiner("Init fence block with id="+id+", name="+name);
			XieBlockFence b;
			if (block==null) {
				b = new XieBlockFence(name, id, getMaterial());
			} else b=(XieBlockFence)block;
			
			block = b;
			
			isDone = expandCommonBlockParameters();
		} else if(subtype.equals("glass")) {
			logFiner("Init glass block with id="+id+", name="+name);
			XieBlockGlass b;
			if (block==null) {
				if (!isKey("material")) b = new XieBlockGlass(name,id);
				else b = new XieBlockGlass(name, id, getMaterial());
				b.setHardness(0.3F);
				b.setStepSound(Block.soundGlassFootstep);
			} else b=(XieBlockGlass)block;
			
			if (isKey("dropSelf")) b.dropBlock=getBoolean("dropSelf");
			if (isKey("inheritMeta")) b.inheritMeta=getBoolean("inheritMeta");
			
			block = b;
			
			isDone = expandCommonBlockParameters();
		} else { // if subtype=="basic"
			// assume basic block if all else fails
			logFiner("Init basic block with id="+id+", name="+name);
			XieBlock b;
			if (block==null) {
				b = new XieBlock(id, getMaterial(), name);
			} else b=(XieBlock)block;
			
			if (isKey("dropSelf")) b.dropBlock=getBoolean("dropSelf");
			if (isKey("inheritMeta")) b.inheritMeta=getBoolean("inheritMeta");
			
			block = b;
					
			isDone = expandCommonBlockParameters();
		}
		
		if (isDone) finaliseBlock();
		return 1;
	}
	
	protected boolean doSapling(int id) {
		logFiner("Init sapling: "+name+"("+id+")");
		XieBlockSapling sapling;
		if (block==null) {
			sapling = new XieBlockSapling(name, id);
		}
		else sapling = (XieBlockSapling) block;
		// set provided plant properties
		Boolean complete = setPlantProps(sapling);
		block = sapling;
		
		if (!complete) {
			logClose();
			return false;
		}
		isDone = expandCommonBlockParameters();
		return true;
	}
	
	protected boolean doFurnace(int id) {
		logFiner("Init furnace: "+name+"("+id+")");
		XieBlockFurnace furnace;
		if (block==null) furnace = new XieBlockFurnace(name, id, getStr("name"), true);
		else furnace = (XieBlockFurnace) block;
		
		boolean complete = setFurnaceProps(furnace);
		
		block = furnace;
				
		if (!complete) return false;
		isDone = expandCommonBlockParameters();
		return true;
	}
	
	protected boolean doChest(int id) {
		logFiner("Init chest: "+name+"("+id+")");
		XieBlockChest chest;
		if (block==null) chest = new XieBlockChest(name, id, getStr("name"));
		else chest = (XieBlockChest) block;
		
		if (isKey("largeName")) chest.setLargeName(getStr("largeName"));
		if (isKey("animate")) chest.animate = getBoolean("animate");
		if (isKey("jammable")) chest.jammable = getBoolean("jammable");
		if (isKey("merge")) {
			chest.mergeHorizontal = getBoolean("merge");
			chest.mergeVertical = getBoolean("merge");	
		}
		if (isKey("mergeHorizontally")) chest.mergeHorizontal = getBoolean("mergeHorizontally");
		if (isKey("mergeVertically")) chest.mergeVertical = getBoolean("mergeVertically");
		if (isKey("vertical")) chest.vertical = getBoolean("vertical");
		if (isKey("openSound")) chest.setOpenSound(getStr("openSound"));
		if (isKey("closeSound")) chest.setCloseSound(getStr("closeSound"));
		if (isKey("mesh")) {
			JsonNode mesh = getNode("mesh");
			if (mesh.getType()==JsonNodeType.STRING) {
				chest.setSmallTex(mesh.getText());
			} else if (mesh.getType()==JsonNodeType.OBJECT) {
				int count = 0;
				
				Map<JsonStringNode, JsonNode> map = mesh.getFields();
				for (JsonNode key : map.keySet()) {
					if (key.getText().toLowerCase().equals("single")) {
						String imgPath = formatTextureFilepath(map.get(key).getText());
						chest.setSmallTex(imgPath);
						logFine("Set texture mesh for single chest to " + imgPath);
						count++;
					} else if (key.getText().toLowerCase().equals("double")) {
						String imgPath = formatTextureFilepath(map.get(key).getText());
						chest.setLargeTex(imgPath);
						logFine("Set texture mesh for double chest to " + imgPath);
						count++;
					}
				}
				
				if (count==0) logIncomplete("Invalid mesh node, 'single' and/or 'double' fields expected");
			} else {
				logIncomplete("Invalid mesh node type");
			}
		}
				
		block = chest;
		
		
		isDone = expandCommonBlockParameters();
		return true;
	}
	
	protected boolean doTree(int id) {
		logFiner("Init leaf block: "+name+"("+id+")");
		XieBlockLeaves leaves;
		if (block==null) {
			leaves = new XieBlockLeaves(name, id);
		}
		else leaves = (XieBlockLeaves) block;
		
		if (isKey("sheds")) {
			Droppables drops = expandDropsNode(getNode("sheds"));
			if (drops!=null && !drops.isEmpty()) leaves.setSheds(drops);
		}
		
		if (isKey("colorize")) leaves.enableColorisation=getBoolean("colorize");
		if (isKey("colorise")) leaves.enableColorisation=getBoolean("colorise");
		
		if (isKey("shape")) leaves.setShape(getStr("shape")); 
		
		if (isKey("shedSaturation")) leaves.shedSaturation = getInt("shedSaturation");
		
		Boolean complete;

		if (isKey("trunkMeta")) {
			int meta[] = Xie.parseArray(getStr("trunkMeta"));
			if (meta!=null) leaves.setTrunkMeta(meta);
			else {
				logInfo("Failed to parse meta array "+getStr("trunkMeta"));
				complete=false;
			}
		}
		
		if (isKey("trunk")) {
			Block trunk = Xie.parseBlock(getStr("trunk"));
			if (trunk==null) complete=false;
			else {
				leaves.setTrunk(trunk);
				complete=true;
			}
		} else complete=true;
		
		block = leaves;
		
		if (!complete) {
			logClose();
			return false;
		}
		isDone = expandCommonBlockParameters();
		return true;
	}
	
	protected boolean doGrass(int id) {
		logFiner("Init grass block: "+name+"("+id+")");
		XieBlockGrass grass;
		if (block==null) {
			grass = new XieBlockGrass(name, id);
		}
		else grass = (XieBlockGrass) block;
		// set provided plant properties
		
		if (isKey("canDie")) grass.grassCanDie=getBoolean("canDie");
		if (isKey("dirt")) {
			BlockID b = Xie.parseBlockAndMetadata(getStr("dirt"));
			if (b.isBlock()) grass.setDirtBlock(b);
			else {
				logDelayed("Failed to identify dirt block "+getStr("dirt"));
				return false;
			}
		}
		if (isKey("lightLevel")) grass.setLightLevel(getInt("lightLevel"));
		if (isKey("colorise")) grass.enableGrassColoring=getBoolean("colorise");
		if (isKey("colorize")) grass.enableGrassColoring=getBoolean("colorize");
		if (!isKey("tex")) grass.useVanillaTexture=true;
		
		block = grass;
		
		isDone = expandCommonBlockParameters();
		return true;
	}
	
	protected boolean doMounted(int id) {
		logFiner("Init mounted block with id="+id+", name="+name);
		XieBlockMounted b;
		if (block==null) {
			b = new XieBlockMounted(name, id, getMaterial());
		} else b=(XieBlockMounted)block;
		
		if (isKey("facing")) b.setCanFace(getStr("facing"));
		if (isKey("isMovable")) b.isMovable=getBoolean("isMovable");
		if (isKey("moveWithItem")) {
			ItemWrapper iw = Xie.parseItemWrapper(getStr("moveWithItem"));
			if (iw==null) {
				logDelayed("Failed to parse item "+getStr("moveWithItem"));
				isDone=false;
				return false;
			}
			b.movableTool = iw.getItemStack();
		}
		
		block = b;

		isDone = expandCommonBlockParameters();
		
		if (!b.boundsVaryByMeta() && b.getBlockBounds()!=null) {
			// bounds have only been set once, assume this is for floor mounting
			// and rotate and populate other block bounds
			float base[] = b.getBlockBounds();
			// base[ 0-width, 1-depth, 2-length, 3-WIDTH, 4-DEPTH, 5-LENGTH ]
			float width[] = new float[]{base[0],base[3]};
			float length[] = new float[]{base[2],base[5]};
			float depth[] = new float[]{base[1],base[4]};
			float _depth[] = new float[]{1-depth[1],1-depth[0]};
			
			float bounds[][] = new float[6][6];
			for (Location l : Location.values()) {
				float x[]; float y[]; float z[];
 				switch(l) {
 				default:
 				case FLOOR: y=depth; x=width; z=length; break;
 				case CEILING: y=_depth; x=width; z=length; break;
 				case NORTH: x=_depth; y=length; z=width; break;
 				case SOUTH: x=depth; y=length; z=width; break;
 				case EAST: z=_depth; x=width; y=length; break;
 				case WEST: z=depth; x=width; y=length; break;
 				}
 				bounds[l.ordinal()]=new float[] {x[0],y[0],z[0],x[1],y[1],z[1]};
			}
			b.setBlockBounds(bounds);
		}
		
		return true;
	}
	
	protected boolean doStem(int id) {
		logFiner("Init stem block with id="+id+", name="+name);
		XieBlockStem b;
		if (block==null) {
			if (!isKey("grows")) {
				logFailure("Stem block missing grows parameter");
				return false;
			}
			
			Block grows = Xie.parseBlock(getStr("grows"));
			if (grows==null) {
				logDelayed("Failed to identify block that stem grows");
				return false;
			}
			
			b = new XieBlockStem(name, id, grows);
		} else b=(XieBlockStem)block;
		
		block = b;
		
		if (isKey("growsOn")) {
			int [] growsOn = Xie.parseBlockIDs(getStr("growsOn"));
			if (growsOn!=null) b.setGrowsOn(growsOn);
		}
		
		if (isKey("plantType")) {
			EnumPlantType t = Xie.parsePlantType(getStr("plantType"));
			if (t!=null) b.setPlantType(t);
		}
		isDone = expandCommonBlockParameters();
		return true;
	}
	
	protected boolean doFacing(int id) {
		logFiner("Init facing block with id="+id+", name="+name);
		XieBlockMounted b;
		if (block==null) {
			b = new XieBlockFacing(name, id, getMaterial());
		} else b=(XieBlockFacing)block;
		
		if (isKey("isMovable")) b.isMovable=getBoolean("isMovable");
		if (isKey("moveWithItem")) {
			ItemWrapper iw = Xie.parseItemWrapper(getStr("moveWithItem"));
			if (iw==null) {
				logDelayed("Failed to parse item "+getStr("moveWithItem"));
				isDone=false;
				return false;
			}
			b.movableTool = iw.getItemStack();
		}
		
		block = b;
		isDone = expandCommonBlockParameters();
		
		if (!b.boundsVaryByMeta() && b.getBlockBounds()!=null) {
			// bounds have only been set once, assume this is for north-facing
			// and rotate and populate other block bounds
			float base[] = b.getBlockBounds();
			// base[ 0-width, 1-height, 2-depth, 3-WIDTH, 4-HEIGHT, 5-DEPTH ]
			float width[] = new float[]{base[0],base[3]};
			float height[] = new float[]{base[1],base[4]};
			float depth[] = new float[]{base[2],base[5]};
			float _depth[] = new float[]{1-depth[1],1-depth[0]};
			
			float bounds[][] = new float[6][6];
			for (Location l : Location.values()) {
				float x[]; float y[]; float z[];
 				switch(l) {
 				default:
 				case FLOOR: y=depth; x=width; z=height; break;
 				case CEILING: y=_depth; x=width; z=height; break;
 				case NORTH: x=_depth; y=height; z=width; break;
 				case SOUTH: x=depth; y=height; z=width; break;
 				case EAST: z=_depth; x=width; y=height; break;
 				case WEST: z=depth; x=width; y=height; break;
 				}
 				bounds[l.ordinal()]=new float[] {x[0],y[0],z[0],x[1],y[1],z[1]};
			}
			b.setBlockBounds(bounds);
		}
		
		return true;
	}
	
	protected boolean setPlantProps(XieBlockPlant plant) {
		logFiner("Setting plant specific properties");
		
		if (isKey("growthRate")) {
			int i = getInt("growthRate");
			if (i>0) plant.growthRate=i;
			else logIncomplete("Invalid growth rate");
		}
		
		if (isKey("growsOn")) {
			int i[] = Xie.parseBlockIDs(getStr("growsOn"));
			if (i!=null) plant.growsOn=i;
			else {
				logDelayed("Could not parse growsOn parameter "+getStr("growsOn"));
				return false;
			}
		}
		if (isKey("growsAgainst")) {
			int i[] = Xie.parseBlockIDs(getStr("growsAgainst"));
			if (i!=null) plant.growsAgainst=i;
			else {
				logDelayed("Could not parse growsAgainst parameter "+getStr("growsOn"));
				return false;
			}
		}
		if (isKey("growthHeight")) plant.growthHeight = getInt(getNode("growthHeight"));
		if (isKey("growsInto")) {
			int i = Xie.parseBlockID(getStr("growsInto"));
			if (i<0) {
				logDelayed("Could not parse growsInto parameter "+getStr("growsInto"));
				return false;
			}
			else plant.growsInto=i;
		}
		
		if (isKey("maxGrowth")) plant.maxGrowth = getInt(getNode("maxGrowth"));
		if (isKey("growsNearWater")) plant.growsNearWater = getInt(getNode("growsNearWater"));
		if (isKey("lightLevel")) plant.lightLevel = getInt(getNode("lightLevel"));
		if (isKey("inheritMeta")) plant.inheritMeta = getBoolean("inheritMeta");
		if (isKey("reproductive")) plant.reproductive = getBoolean(getNode("reproductive"));
		if (isKey("reproductionRate")) plant.reproductionRate = getInt(getNode("reproductionRate"));
		if (isKey("fertilisable")) plant.fertilisable = getBoolean(getNode("fertilisable"));
		if (isKey("fertilizable")) plant.fertilisable = getBoolean(getNode("fertilizable"));
		if (isKey("bonemeal")) if (getBoolean("bonemeal")) Xie.addBonemealable(plant);
		
		return true;
	}
	
	protected boolean setFurnaceProps(XieBlockFurnace furnace) {
		logFiner("Setting furnace properties");
		boolean complete = true;
		
		if (isKey("idle")) {
			XieBlockFurnace idle = (XieBlockFurnace) Xie.parseBlock(getStr("idle"));
			if (idle==null) {
				logDelayed("Failed to parse idle furnace block "+getStr("idle"));
				return false;
			}
			else furnace.setIdleVersion(idle);
		}
		
		if (isKey("lightValue")) furnace.setLightValue(getFloat("lightValue"));
		else furnace.setLightValue(0.875F);
		
		if (isKey("initialBurnTime")) furnace.initialBurnTime=getInt(getNode("initialBurnTime"));
		
		if (isKey("naked")) furnace.naked=getBoolean("naked");
		if (isKey("volatility")) furnace.setVolatility(getInt("volatility"));
		
		if (isKey("burnLevels")) {
			// TODO support types other than string
			String burnlvls[] = getStr("burnLevels").split(",");
			int burnLevels[] = new int[burnlvls.length];
			for (int i=0; i<burnlvls.length; i++) {
				try {
					burnLevels[i]=Integer.parseInt(burnlvls[i]);
				} catch (NumberFormatException e) {
					logIncomplete("Failed to parse burn level, number format exception @"+i+": "+getStr("burnLevels"));
					complete = false;
				}
			}
			
			if (complete) {
				logFiner("Burn levels ("+burnLevels.length+") set!");
				furnace.setBurnLevels(burnLevels);
			} else {
				logIncomplete("Failed to correctly parse burn levels");
			}
		}
		
		if (isKey("cookTime")) furnace.setCookTime(getInt(getNode("cookTime")));
		
		if (isKey("fuels")) {
			Map<ItemWrapper,Integer> fuels = new HashMap();
			Map<JsonStringNode, JsonNode> map = getNode("fuels").getFields();
			for (JsonNode key : map.keySet()) {
				if (key.getText().equals("#furnace")) {
					logFiner("Standard furnace fuels enabled");
					furnace.fuelsAsFurnace=true;
					continue;
				}
				ItemWrapper is = Xie.parseItemWrapper(key.getText());
				if (is==null) {
					logDelayed("Failed to parse fuel itemstack "+key.getText());
					complete=false;
					continue;
				}
				int burnTime = Integer.parseInt(map.get(key).getText());
				if (burnTime>0) fuels.put(is,burnTime);
				else logInfo("Fuel "+key.getText()+" returned burnTime "+burnTime);
			}
			int max=0;
			for (Integer i : fuels.values()) if (i>max) max=i;
			furnace.fuels=fuels;
			furnace.setBurnLevelScaleMax(max);
			logFiner("Fuels("+fuels.size()+") set!");
		}
		
		if (isKey("smelts")) {
			Map<ItemWrapper,ItemWrapper> smeltables = furnace.smeltables;
			JsonNode node = getNode("smelts");
			if (node.getType()==JsonNodeType.STRING) {
				for (String s : node.getText().split(",")) {
					if (s.startsWith("#")) {
						// interpret implicit group
						if (s.substring(1).equals("food")) addFoodSmeltables(smeltables);
						else if (s.substring(1).equals("furnace")) furnace.smeltsAsFurnace=true;
						continue;
					}
					
					ItemWrapper in = null;
					ItemWrapper out = null;
					if (s.contains("->")) {
						String ss[] = s.split("->");
						if (ss.length>0) in = Xie.parseItemWrapper(ss[0]);
						if (ss.length>1) out = Xie.parseItemWrapper(ss[1]);
						if (out==null) logDelayed("Failed to parse output itemstack for "+s);
					} else in = Xie.parseItemWrapper(s);
					if (in==null) {
						logDelayed("Failed to parse itemstack "+s);
						complete=false;
					}
					
					if (out==null) {
						// find default smeltable
						ItemStack is = (ItemStack) FurnaceRecipes.smelting().getSmeltingList().get(in.id);
						if (is==null){
							logIncomplete("No default smelting result found for "+s+", ignoring.");
							continue;
						} else out = new ItemWrapper(is);
					}
					
					smeltables.put(in,out);
				}
			} else if (node.getType()==JsonNodeType.OBJECT) {
				Map<JsonStringNode, JsonNode> map = node.getFields();
				for (JsonNode key : map.keySet()) {
					ItemWrapper in = Xie.parseItemWrapper(key.getText());
					ItemWrapper out = Xie.parseItemWrapper(map.get(key).getText());
					
					if (in==null) {
						logDelayed("Failed to id smeltable "+key.getText());
						complete=false;
					}
					
					if (out==null) {
						// find default smeltable
						out = (ItemWrapper) FurnaceRecipes.smelting().getSmeltingList().get(in.id);
						if (out==null) {
							logIncomplete("No default smelting result found for "+key.getText()+", ignoring.");
							continue;
						}
					}
					
					smeltables.put(in,out);
				}
			} else {
				logIncomplete("Unsupported smeltables node type");
				return false;
			}
			
			furnace.smeltables=smeltables;
			logFiner("Smeltables("+smeltables.size()+") set!");
		}
		
		return complete;
	}
	
	protected void addFoodSmeltables(Map<ItemWrapper, ItemWrapper> smeltables) {
		smeltables.put(new ItemWrapper(Item.porkRaw), new ItemWrapper(Item.porkCooked));
		smeltables.put(new ItemWrapper(Item.beefRaw), new ItemWrapper(Item.beefCooked));
		smeltables.put(new ItemWrapper(Item.chickenRaw), new ItemWrapper(Item.chickenCooked));
		smeltables.put(new ItemWrapper(Item.fishRaw), new ItemWrapper(Item.fishCooked));
		smeltables.putAll(foodSmeltsSoFar);
		foodSmeltMapsToUpdate.add(smeltables);
	}
	
	protected void finaliseBlock() {
		logFiner("Finalising block");
		ModLoader.addName(block.getBlock(), name);
		if (block.hasSubTypes()) {
			GameRegistry.registerBlock(block.getBlock(), XieItemBlock.class, name);
			String names[] = block.getNames();
			if (names!=null) {
				int d = 0;
				for (String name : names)
					ModLoader.addName(new ItemStack(block.getBlock().blockID,1,d++),name);
			}
		}
		else GameRegistry.registerBlock(block.getBlock(), name);
		Work.instance().blockAdded(id);
		logInfo(" === Block Job done! === ");
	}
	
	// sets common block parameters, if present
	// e.g. stepsound, hardness
	boolean addedSpawn = false;
	protected boolean expandCommonBlockParameters() {
		boolean isDone = true;
		
		if (block==null) return false;
		
		if (isKey("stepsound")) {
			block.setStepSound(identifyStepSound(getStr("stepsound")));
		} else if (block.getBlock().stepSound==null) block.setStepSound(Block.soundGrassFootstep); // arbitrary default
		
		if (isKey("hardness")) block.setHardness(getFloat("hardness"));
		
		if (isKey("spawn") && !addedSpawn) {
			Work.instance().addSpawnJob(block.getUnlocalizedName(),getNode("spawn"));
			addedSpawn=true;
		}
		
		if (isKey("resistance")) block.getBlock().setResistance(getFloat("resistance"));
		
		if (isKey("tool")) expandToolEffectivenessNode(getNode("tool"));
		
		if (isKey("drops")) {
			Droppables drops = expandDropsNode(getNode("drops"));
			if (drops==null) {
				logDelayed("Failed to identify drops");
				isDone=false;
			} else if (problemFlag) {
				problemFlag = false;
				isDone = false;
			}
			else block.setDrops(drops);
		}
		
		if (isKey("canBePlacedOn")) {
			List<Block> blockList = Xie.parseBlockList(getStr("canBePlacedOn"));
			if (blockList==null) {
				logIncomplete("Failed to parse one or more blocks in canBePlacedOn list");
			} else {
				block.setCanBePlacedOn(blockList);
			}
		}
		
		if (isKey("renderType")) block.setRenderType(Xie.parseRenderType(getStr("renderType")));
		
		if (isKey("blockBounds")) expandBlockBoundsNode(getNode("blockBounds"));

		if (isKey("lightValue")) block.setLightValue(getFloat("lightValue"));
		
		if (isKey("lightOpacity")) {
			float f = getFloat("lightOpacity");
			if (f>1) block.setLightOpacity((int)f);
			else block.setLightOpacity((int)(255*f));
		}
		
		if (isKey("cooked") || isKey("smelted")) {
			return parseSmeltingRecipe();
		}
		
		if (isKey("names")) {
			String names[] = expandNamesNode(getNode("names"));
			String namess = "";
			for (String n : names) namess+=n+",";
			namess=namess.substring(0,namess.length()-1);
			logFiner("Set names: "+namess);
			block.setNames(names);			
		}
		
		if (isKey("fuel")) expandFuelNode(block.getBlock().blockID);
		
		if (isKey("becomesWhenDestroyed")) {
			int bwd[] = Xie.parseBlockAndMeta(getStr("becomesWhenDestroyed"));
			if (bwd==null) {
				logDelayed("Failed to identify block and/or meta: "+getStr("becomesWhenDestroyed"));
				isDone=false;
			} else block.setBecomesWhenDestroyed(bwd);
		}
		
		if (isKey("opaque")) block.setIsOpaque(getBoolean("opaque"));
		
		if (isKey("walkthroughable")) block.walkThroughable(getBoolean(getNode("walkthroughable")));
		
		if (isKey("spikey")) block.setIsSpikey(getBoolean("spikey"));
		
		if (isKey("dungeonLoot")) doDungeonLoot(getNode("dungeonLoot"));
		
		if (isKey("textureBase")) doTextureBase();
		
		return isDone;
	}
	
	protected void doTextureBase() {
		String base = getStr("textureBase");
		
		Block b;
		// SHAMELESS HACK
		if (base.equals("crops")) b = Block.crops;
		else b = Xie.parseBlock(base);
		if (b!=null) {
			block.setTextureBase(b);
		}
	}

	// returns material if present, otherwise returns a default
	protected Material getMaterial() {
		Material material = Material.ground; // default
		
		if (isKey("material")) return Xie.parseMaterial(getStr("material"));
		
		return material;
	}
	
	// TODO add support for meta-data sensitive JSON object
	protected boolean parseSmeltingRecipe() {
		int id = -1;
		id = block.getBlock().blockID;
		if (id<0) return false;
		
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
	
	protected boolean expandToolEffectivenessNode(JsonNode node) {
		if (node.getType()!=JsonNodeType.STRING) {
			logFailure("Invalid block object tool node type, String expected");
			return false;
		}
		
		String str = node.getText();
		String sss[] = str.split(",");
		
		// First pass checks that all tool identifiers are resolvable
		for (String s : sss) {
			s = s.toLowerCase().trim();
			// identify tool this string is referring to. Could be...
			// a) Reference to vanilla tool set (pickaxe, axe or shovel)
			if (s.equals("pickaxe")) continue;
			if (s.equals("axe")) continue;
			if (s.equals("shovel")) continue;
			// b) Reference to custom tool item
			Item i = Xie.parseItem(s);
			if (i!=null && i instanceof XieItemTool) continue;
			else {
				logDelayed("Failed to identify tool item "+s);
				return false;
			}
			// c) reference to custom tool set
			// TODO - need to implement custom tool sets first :P
		}
		
		// Second pass checks resolves all tool identifiers
		for (String s : sss) {
			s = s.toLowerCase().trim();
			// identify tool this string is referring to. Could be...
			// a) Reference to vanilla tool set (pickaxe, axe or shovel)
			if (s.equals("pickaxe")) {
				Xie.setPickaxeEffectiveness(block.getBlock());
				continue;
			}
			if (s.equals("axe")) {
				Xie.setAxeEffectiveness(block.getBlock());
				continue;
			}
			if (s.equals("shovel")) {
				Xie.setShovelEffectiveness(block.getBlock());
				continue;
			}
			// b) Reference to custom tool item
			Item i = Xie.parseItem(s);
			if (i!=null && i instanceof XieItemTool) {
				((XieItemTool)i).addBlock(block.getBlock());
			} else {
				logDelayed("Failed to identify tool item "+s);
				return false;
			}
			// c) reference to custom tool set
			// TODO - need to implement custom tool sets first :P
		}
		
		return true;
	}
	
	protected boolean expandBlockBoundsNode(JsonNode node) {
		if (node.getType()==JsonNodeType.STRING) {
			boolean failFlag = false;
			String str = node.getText();
			String split[] = str.split(",");
			if (split.length>=6) {
				float bounds[] = new float[6];
				int i = 0;
				for (String s : split) {
					float n = -1;
					try {
						n = Float.parseFloat(s);
					} catch (NumberFormatException e) {
						logIncomplete("Failed to correctly parse float @i :"+str);
						continue;
					}
					if (n!=-1) {
						bounds[i++] = n;
						if (i>=bounds.length) break;
					}
				}
				
				if (i>=bounds.length) {
					block.setBlockBounds(bounds);
				} else failFlag = true;
			} else failFlag = true;
			
			if (failFlag) logIncomplete("Invalid block bounds ["+str+"]");
		} else if (node.getType()==JsonNodeType.OBJECT) { 
			Map<Integer,float[]> boundsMap = new HashMap();
			Map<JsonStringNode, JsonNode> map = node.getFields();
			for (JsonNode key : map.keySet()) {
				int meta[]=null;
				meta = Xie.parseMetaRange(key.getText());
				if (meta==null) {
					logIncomplete("Failed to parse meta range "+key.getText());
					return false;
				}
				
				for (int m=meta[0]; m<=meta[1]; m++) {
					float bounds[] = new float[]{0,1,0,1,0,1};
					JsonNode boundsNode = map.get(key);
					if (boundsNode.getType()==JsonNodeType.STRING) {
						String boundsTxt[] = boundsNode.getText().split(",");
						for (int i=0; i<bounds.length; i++) {
							try {
								bounds[i]=Float.parseFloat(boundsTxt[i]);
							} catch (NumberFormatException e) {
								logIncomplete("Failed to parse float @"+i+" from "+boundsNode.getText()+" ignoring...");
								continue;
							}
						}
					} else {
						// TODO support other types
						logIncomplete("Unsupported block bounds node type");
					}
	
					boundsMap.put(m, bounds);
				}
			}
			
			int max = 0;
			for (int i : boundsMap.keySet()) if (i>max) max=i;
			
			float blockBounds[][] = new float[max][6];
			for (int i=0; i<max; i++) {
				if (boundsMap.containsKey(i)) blockBounds[i]=boundsMap.get(i);
				else if (i>0) blockBounds[i]=blockBounds[i-1];
				else blockBounds[i]=new float[]{0,1,0,1,0,1};
			}
			
			block.setBlockBounds(blockBounds);
		} else {
			// TODO support other boundary node formats
			logIncomplete("Unsupported block boundary node type");
		}
		
		return true;
	}
	

	
	protected void doDungeonLoot(JsonNode j) {
		String s = j.getText();
		
		int rarity=1;
		if (s.contains("@")) {
			String ss[] = s.split("@");
			rarity = Integer.parseInt(ss[1]);
			s=ss[0];
		}
		
		int min=1;
		if (s.contains("-")) {
			String ss[] = s.split("-");
			min = Integer.parseInt(ss[0]);
			s=ss[1];
		}
		int max = Integer.parseInt(s);
		
		Xie.addDungeonLoot(new DungeonLootHelper(new ItemStack(block.getBlock()), rarity, min, max));
	}
	
}
