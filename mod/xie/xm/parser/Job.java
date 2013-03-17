package mod.xie.xm.parser;

import java.awt.image.BufferedImage;

import mod.xie.xm.Work;
import mod.xie.xm.file.FileManager;
import mod.xie.xm.file.PathManager;
import mod.xie.xm.file.SpritesheetManager;
import mod.xie.xm.file.XieLog;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.ContentManager;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.JobType;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;



import net.minecraft.block.StepSound;
import net.minecraft.src.ModTextureStatic;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;
import argo.jdom.JsonStringNode;

public abstract class Job {		
	public JobType type;
	protected String parent; // the parent filename for this job
	protected String name;
	private Map<String,JsonNode> content;
	protected JsonNode jNode;
	protected Boolean isDone;
	protected boolean problemFlag = false;
	public Boolean isFail;
	protected String failMsg;
	protected String path;
	
	public final static String X = "@";
	
	// list of maps to add new food smelts to
	protected static List<Map<ItemWrapper,ItemWrapper>> foodSmeltMapsToUpdate = new LinkedList();
	// list of food smelts parsed so far
	protected static Map<ItemWrapper,ItemWrapper> foodSmeltsSoFar = new HashMap();

	public String fullname() { return parent+X+name; }
	public String name() {return name;}
	public String parent() {return parent;}
	
	public JsonNode getNode() { return jNode; }

	public Job(JobType t, String n, JsonNode node) {
		type=t;
		if (n.contains(X)) {
			String split[] = n.split(X);
			name = split[1];
			parent = split[0];
		} else {
			name=n;
			parent="root";
		}
		jNode=node;
		isDone=false;
		isFail=false;
		
		content = new HashMap();
		if (node.getType()==JsonNodeType.OBJECT) {
			Map<JsonStringNode, JsonNode> map = node.getFields();
			for (JsonNode key : map.keySet()) content.put(key.getText().toLowerCase(),map.get(key));
		}
	}
	
	public void logAsIncomplete() {
		logIncomplete("Job unresolved - "+failMsg);
	}
	
	public String getFailReason() {
		if (failMsg==null) return "Colonel Failure";
		return failMsg; 
	}
	
	protected void logFailure(String s) {
		isFail=true;
		XieLog.log().logFailure(getDescriptor()+" "+s);
		failMsg=s;
	}
	
	protected void logIncomplete(String s) {
		XieLog.log().logIncomplete(getDescriptor()+" "+s);
		failMsg=s;
	}
	
	protected void logDelayed(String s) {
		XieLog.log().logDelayed(getDescriptor()+" "+s);
		failMsg=s;
	}
	
	// log info message (job completions mostly)
	protected void logInfo(String s) {
		XieLog.log().info(getDescriptor()+" "+s);
	}
	
	// log fine detail message (arguably important stuff)
	protected void logFine(String s) {
		XieLog.log().fine(getDescriptor()+" "+s);
	}
	
	// log finer detail message (nitty gritty stuff)
	protected void logFiner(String s) {
		XieLog.log().finer(getDescriptor()+" "+s);
	}
	
	protected String getStr(String key) {
		return getNode(key).getText();
	}
	
	public abstract int doWork();
	
	protected Boolean isKey(String string) {
		if (!content.containsKey(string.toLowerCase())) return false;
		else {
			// logFiner("Found key "+string);
			return true;
		}
	}
	
	protected JsonNode getNode(String string) {
		return content.get(string.toLowerCase());
	}
	
	protected Boolean getBoolean(JsonNode node) {
		if (node.getType()==JsonNodeType.TRUE) return true;
		else if (node.getType()==JsonNodeType.FALSE) return false;
		else if (node.getType()==JsonNodeType.STRING) {
			if (node.getText().toLowerCase().equals("true")) return true;
			else return false;
		} else if (node.getType()==JsonNodeType.NUMBER) {
			if (getInt(node)>1) return true;
			else return false;
		} else return false;
	}
	
	protected Boolean getBoolean(String s) {
		return getBoolean(getNode(s));
	}
	
	// checks for the existence of one or more keys in content
	protected Boolean validate(Map<String,JsonNode> map, String keys) {
		Boolean validation = true;

		String key[] = keys.split(",");
		for (int i=0; i<key.length; i++) {
			key[i]=key[i].trim().toLowerCase();
			if (!map.containsKey(key[i])) {
				logDelayed("Validation of ["+key[i]+"] failed");
				validation = false;
			}
		}

		return validation;
	}
	
	protected void expandFuelNode(int id) {
		if (isKey("fuel")) {
			JsonNode node = getNode("fuel");
			if (node.getType()==JsonNodeType.NUMBER) {
				Xie.addFuel(id, getInt(node));
				logFiner("Added as fuel, burntime="+getInt(node));
			} else if (node.getType()==JsonNodeType.OBJECT) {
				Map<JsonStringNode, JsonNode> map = node.getFields();
				for (JsonNode key : map.keySet()) {
					int meta[] = Xie.parseMetaRange(key.getText());
					if (meta==null) {
						logIncomplete("Invalid meta range for fuel, "+key.getText());
						continue;
					}
					if (meta.length==1) {
						Xie.addFuel(id, meta[0], getInt(map.get(key)));
						logFiner("Added as fuel, burntime="+getInt(map.get(key)));
					}
					else {
						for (int i=meta[0]; i<=meta[1]; i++) {
							Xie.addFuel(id, i, getInt(map.get(key)));
							logFiner("Added subitem("+i+") as fuel, burntime="+getInt(map.get(key)));
						}
					}
				}
			} else {
				logIncomplete("Invalid fuel node type: "+node.getType());
			}
		}
	}

	protected int getID() {
		if (isKey("id")) return getID(getStr("id"));
		else if (isKey("overide")) return getID(getStr("overide"));
		else if (isKey("override")) return getID(getStr("override"));
		else return Work.instance().getIdFor(name);
	}

	protected int getID(String s) {
		if (Xie.isNumbers(s)) return Integer.parseInt(s);
		else return Xie.parseBlockOrItemId(s);
	}


	protected int getInt(JsonNode node) {
		return Integer.parseInt(node.getText());
	}

	protected int getInt(String s) {
		return Integer.parseInt(getNode(s).getText());
	}

	protected float getFloat(JsonNode node) {
		float f = Float.parseFloat(node.getText());
		return f;
	}
	
	protected float getFloat(String s) {
		float f = Float.parseFloat(getNode(s).getText());
		return f;
	}

	protected Droppables expandDropsNode(JsonNode node) {
		logFiner("Attempting to add drops");
		Droppables droppables = new Droppables();
		
		if (node.getType()==JsonNodeType.STRING) {
			logFiner("Identifying drop(s) "+node.getText());
			String drops[] = node.getText().split(",");
			for (String drop : drops) {
				ItemStack is = Xie.parseItemStack(drop);
				if (is==null) {
					logDelayed("Drop not found "+drop);
					problemFlag = true;
					continue;
				}
				droppables.add(new Drop(is));
			}
			return droppables;
		} else if (node.getType()!=JsonNodeType.OBJECT) {
			logIncomplete("Invalid drop node type "+node.getType().toString());
		}
		
		Map<JsonStringNode, JsonNode> drops = node.getFields();
		for (JsonNode key : drops.keySet()) {
			logFiner("Identifying drop "+key.getText());
			ItemStack is = Xie.parseItemStack(key.getText());
			if (is==null) {
				logDelayed("Drop not found "+key.getText());
				problemFlag = true;
				continue;
			}
			Drop drop = new Drop(is);
			
			JsonNode val = drops.get(key);
			Map<JsonStringNode, JsonNode> map = val.getFields();
			
			for (JsonNode n : map.keySet()) {
				if (n.getText().equals("chance")) {
					JsonNode chanceNode = map.get(n);
					if (chanceNode.getType()==JsonNodeType.NUMBER
						|| chanceNode.getType()==JsonNodeType.STRING) {
						int chance = getInt(chanceNode);
						logFiner("Chance="+chance);
						drop.setChance(chance);
					} else if (chanceNode.getType()==JsonNodeType.OBJECT) {
						Map<JsonStringNode, JsonNode> chanceMap = chanceNode.getFields();
						for (JsonNode metaRange : chanceMap.keySet()) {
							String range = metaRange.getText();
							if (range==null) {
								logIncomplete("Null range found");
								continue;
							}
							int chance = getInt(chanceMap.get(metaRange));
							if (chance<0) chance=0;
							logFiner("Chance(for meta "+metaRange.getText()+")="+chance);
							drop.setChance(metaRange.getText(), chance);
						}
					}
				}
				
				if (n.getText().equals("amount")) {
					int amt = getInt(map.get(n));
					if (amt>0) {
						logFiner("Amount="+amt);
						drop.setQuantity(amt);
					}
				} 
				
				if (n.getText().equals("inheritMeta")) {
					drop.inheritMeta = getBoolean(map.get(n));
				}
			}
			
			droppables.add(drop);
		}
		
		return droppables;
	}
	
	protected String[] expandNamesNode(JsonNode names) {
		String n[] = names.getText().split(",");
		for (String s : n) s=s.trim();
		
		return n;
	}
	
	protected int expandTextureNode(JsonNode texNode) {
		if (texNode.getType()==JsonNodeType.STRING) {
			if (texNode.getText().startsWith("#")) {
				// reference to existing block texture
				String texReference = texNode.getText().substring(1);
				int side = 0;
				if (texReference.contains(":")) {
					String refSplit[] = texReference.split(":");
					texReference=refSplit[0];
					side = Xie.parseSide(refSplit[1]);
				}
				Block b = Xie.parseBlock(texReference);
				if (b==null) {
					logIncomplete("Failed to identify referenced block "+texReference);
					return XieTex.failTex; // TODO FTFESAD return failtex or -1?
				} else return b.getBlockTextureFromSide(side);
			} else {
				String texFile = formatTextureFilepath(texNode.getText());
				int texID = -1;
				if (texFile.startsWith("http://")) {
					texID = loadURLTexture(texFile, true);
				} else if (texFile!=null) texID = Xie.getBlockTexOverride(texFile);
				
				if (texID<0) {
					logIncomplete("Texture missing: "+texNode.getText());
					return XieTex.failTex;
				} else return texID;
			}
		} else if (texNode.getType()==JsonNodeType.NUMBER) {
			int texID = Integer.parseInt(texNode.getText());
			return texID;
		} else return XieTex.failTex;
	}
	
	protected String formatTextureFilepath(String text) {
		return formatTextureFilepath(text,true);
	}
	
	protected String formatTextureFilepath(String text, boolean addToTextureRecord) {
		return SpritesheetManager.get().getTextureFilepath(fullname(),path,text,addToTextureRecord);
	}

	public String getDescriptor() {
		return "["+getType(type)+"."+name+"]";
	}
	
	@Override
	public String toString() {
		String r = "[Job: "+name+"; "+getType(type);
		if (content==null) r+="{"+jNode.toString()+"}]";	
		else r+="{"+content.toString()+"}]";
		
		return r;
	}

	public Boolean isDone() {
		return isDone;
	}
	
	protected StepSound identifyStepSound(String step) {
		step = step.toLowerCase();
		if (step.equals("cloth")) return Block.soundClothFootstep;
		else if (step.equals("glass")) return Block.soundGlassFootstep;
		else if (step.equals("grass")) return Block.soundGrassFootstep;
		else if (step.equals("gravel")) return Block.soundGravelFootstep;
		else if (step.equals("metal")) return Block.soundMetalFootstep;
		else if (step.equals("powder")) return Block.soundPowderFootstep;
		else if (step.equals("sand")) return Block.soundSandFootstep;
		else if (step.equals("stone")) return Block.soundStoneFootstep;
		else if (step.equals("wood")) return Block.soundWoodFootstep;
		else return Block.soundPowderFootstep; // default
	}
	
	protected List<Integer> expandBlockListNode(JsonNode node) {
		if (node.getType()==JsonNodeType.NUMBER) {
			int blockID = Integer.parseInt(node.getText());
			logFiner("Parsed blockID: "+blockID+" from "+node.getText());
			List<Integer> blocks = new LinkedList<Integer>();
			blocks.add(blockID);
			return blocks;
		} else if (node.getType()==JsonNodeType.STRING) {
			List<Integer> list = Xie.parseBlockIDsList(node.getText());
			if (list!=null) return list;
			else return null;
		} else {
			logIncomplete("Unsupported block list node format: "+node.getType()); 
			return new LinkedList<Integer>();
		}
	}
	
	protected List<Integer> blocksToBlockIDs(List<Block> blocks) {
		List<Integer> blockIDs = new LinkedList<Integer>();
		for (Block b : blocks) blockIDs.add(b.blockID);
		return blockIDs;
	}
	
	protected boolean isString(JsonNode node) {
		return node.getType()==JsonNodeType.STRING;
	}
	
	protected boolean isObject(JsonNode node) {
		return node.getType()==JsonNodeType.OBJECT;
	}
	
	public static String getType(JobType t) {
		switch (t) {
		case BLOCK: return "block";
		case ITEM: return "item";
		case RECIPE: return "recipe";
		case SPAWN: return "spawn";
		case ALIAS: return "alias";
		default: return "undefined";
		}
	}
	
	public static String getTypePlural(JobType t) {

		switch (t) {
		case BLOCK: return "blocks";
		case ITEM: return "items";
		case RECIPE: return "recipes";
		case SPAWN: return "spawns";
		case ALIAS: return "aliases";
		default: return "undefined";
		}
	}
	
	public static JobType getType(String s) {
		if (s.equals("blocks")) return JobType.BLOCK;
		if (s.equals("block")) return JobType.BLOCK;
		if (s.equals("items")) return JobType.ITEM;
		if (s.equals("item")) return JobType.ITEM;
		if (s.equals("recipes")) return JobType.RECIPE;
		if (s.equals("recipe")) return JobType.RECIPE;
		if (s.equals("spawns")) return JobType.SPAWN;
		if (s.equals("spawn")) return JobType.SPAWN;
		if (s.equals("aliases")) return JobType.ALIAS;
		if (s.equals("alias")) return JobType.ALIAS;
		else return null;
	}
	
	protected void logOpen() {
		logFine("Opening job...");
	}
	
	protected void logClose() {
		isDone=false;
		logFine("... Job incomplete. Bumping back into queue.");
	}

	@SideOnly (Side.CLIENT)
	protected int loadURLTexture(String urltxt, boolean blockTexture) {
		URL url;
		try {
			logFine("Processing URL "+urltxt);
			url = new URL(urltxt);
			String filename = downloadImage(url);
			return blockTexture ? Xie.getBlockTexOverride(formatTextureFilepath(filename)) 
								: Xie.getItemSpriteOverride(formatTextureFilepath(filename));
		} catch (MalformedURLException e) {
			logIncomplete("Malformed URL: "+urltxt);
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			logIncomplete("Failed to save URL texture: "+urltxt);
			e.printStackTrace();
			return -1;
		}
	}
	
	@SideOnly (Side.CLIENT)
	protected String loadURLSpritesheet(String urltxt) {
		URL url;
		try {
			logFine("Processing URL "+urltxt);
			url = new URL(urltxt);
			String filename = downloadImage(url);	
			return filename;
		} catch (MalformedURLException e) {
			logIncomplete("Malformed URL: "+urltxt);
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			logIncomplete("Failed to save URL texture: "+urltxt);
			e.printStackTrace();
			return null;
		}
	}
	
	protected String downloadImage(URL url) throws IOException {
		String localPath = url.getPath();
		String filename = PathManager.get().cacheDir() + localPath;
		File file = new File(filename);
		
		if (!file.exists()) {
			logFine("Not found locally("+filename+"), downloading");
			
			if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
			file.createNewFile();
						
			InputStream reader = url.openStream();
			FileOutputStream writer = new FileOutputStream(file);
			
			byte buffer[] = new byte[131072];
			for (int n = reader.read(buffer); n>0; n = reader.read(buffer)) {
				writer.write(buffer,0,n);
			}
			
			writer.close();
		} else {
			logFine("File found locally ("+filename+"), download not required");
		}
		
		localPath = PathManager.get().localContentPath() + localPath;
		return localPath;
	}
	
/*	protected int loadURLTexture(String txt, boolean blockTexture) {
		if (Xie.isServer()) return -1;

		// process URL
		int index = Xie.getUniqueSpriteIndex(blockTexture?"/terrain.png":"/gui/items.png");
		BufferedImage image;
		try {
			image = ImageIO.read(new URL(txt));
		} catch (MalformedURLException e) {
			logIncomplete("Funky URL: "+txt);
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			logIncomplete("IOException reading from URL: "+txt);
			e.printStackTrace();
			return -1;
		}
		Xie.registerTexture(index, blockTexture?0:1, image);
		return index;
	}*/
}
