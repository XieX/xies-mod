package xie.mods.xm.parser;

import java.util.HashMap;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import xie.mods.xm.util.ItemWrapper;
import xie.mods.xm.util.JobType;
import xie.mods.xm.util.Spawnable;
import xie.mods.xm.util.Xie;


import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;
import argo.jdom.JsonStringNode;

public class JobSpawn extends Job {

	public static enum SpawnType {
		surface, nether, dungeon, ore
	}
	
	protected Spawnable spawn;
	protected SpawnType type;
	
	public JobSpawn(String n, JsonNode node) {
		super(JobType.SPAWN, n, node);
	}

	// TODO validate that spawns on/in/near blocks have been correctly identified
	@Override
	public int doWork() {
		logOpen();
		// first check for special job name "dungeonLoot"
		if (name.equals("dungeonLoot")) return doDungeonLoot();
		
		// identify block to be spawned from name or property
		spawn = initSpawn();
		if (spawn==null) {
			logDelayed("Failed to identify block to be spawned");
			return 0;
		}
		
		if (isKey("type")) {
			String str = getStr("type").toLowerCase().trim();
			if (str.equals("surface")) type = SpawnType.surface;
			else if (str.equals("nether")) type = SpawnType.nether;
			else if (str.equals("dungeon")) type = SpawnType.dungeon;
			else if (str.equals("ore")) {
				type = SpawnType.ore;
				spawn.clump = true;
			}
			else {
				logIncomplete("Failed to identify spawn type "+str+", defaulting to 'surface'");
				type=SpawnType.surface;
			}
		} else type = SpawnType.surface;
	
		if (isKey("rarity")) {
			JsonNode node = getNode("rarity");
			if (node.getType()==JsonNodeType.OBJECT) {
				// expand metarange node
				Map<JsonStringNode, JsonNode> map = node.getFields();
				for (JsonNode key : map.keySet()) {
					int metas[] = Xie.parseMetaRange(key.getText());
					if (metas!=null && metas.length==2) {
						int spawnFactor = getInt(map.get(key));
						if (spawnFactor<1) {
							logIncomplete("Invalid spawn factor/rarity "+map.get(key).getText());
						}
						for (int meta=metas[0]; meta<=metas[1]; meta++)
							spawn.addMetaSpawnFactor(meta, spawnFactor);
					} else {
						logIncomplete("Invalid meta range "+key.getText());
					}
				}
			} else {
				// assume int
				spawn.spawnFactor=getInt(node);
			}
		}

		if (isKey("spawnson")) {
			List<Integer> spawnsOn = expandBlockListNode(getNode("spawnson"));
			if (spawnsOn==null) {
				logDelayed("Failed to correctly parse all spawns on blocks");
				return 0;
			}
			spawn.spawnsOn=spawnsOn;
		}

		if (isKey("spawnsin")) {
			// for now assume str
			List<Integer> spawnsIn = expandBlockListNode(getNode("spawnsin"));
			if (spawnsIn==null) {
				logDelayed("Failed to correctly parse all spawns in blocks");
				return 0;
			}
			spawn.spawnsIn=spawnsIn;
		}

		if (isKey("spawnsNear")) {
			List<Integer> spawnsNear = expandBlockListNode(getNode("spawnsNear"));
			if (spawnsNear==null) {
				logDelayed("Failed to correctly parse all spawns near blocks");
				return 0;
			}
			spawn.spawnsNear=spawnsNear;
			if (isKey("nearDistance")) {
				int nearDistance = getInt(getNode("nearDistance"));
				if (nearDistance>0) spawn.nearDistance=nearDistance;
			}
		}

		if (isKey("spawnsUnder")) {
			List<Integer> spawnsUnder = expandBlockListNode(getNode("spawnsUnder"));
			if (spawnsUnder==null) {
				logDelayed("Failed to correctly parse all spawns under blocks");
				return 0;
			}
			spawn.spawnsUnder=spawnsUnder;
		}

		if (isKey("biomes")) {
			String biomes[] = getNode("biomes").getText().split(",");
			for (int i=0; i<biomes.length; i++)
				biomes[i] = biomes[i].trim().toLowerCase();
			spawn.biomes=biomes;
		}
		
		if (isKey("cluster")) {
			JsonNode node = getNode("cluster");
			if (node.getType()!=JsonNodeType.OBJECT) {
				logFailure("Invalid cluster node type");
				return 0;
			}
		
			// expand metarange node
			Map<Byte,Integer> clusterSpawnRateMap = new HashMap<Byte,Integer>();
			Map<JsonStringNode, JsonNode> map = node.getFields();
			for (JsonNode key : map.keySet()) {
				int metas[] = Xie.parseMetaRange(key.getText());
				if (metas!=null && metas.length==2) {
					int spawnFactor = getInt(map.get(key));
					if (spawnFactor<1) {
						logIncomplete("Invalid spawn factor/rarity "+map.get(key).getText());
					}
					for (int meta=metas[0]; meta<=metas[1]; meta++)
						clusterSpawnRateMap.put((byte)meta, spawnFactor);
				} else {
					logIncomplete("Invalid meta range "+key.getText());
				}
			}
			spawn.setClusterMetaFactors(clusterSpawnRateMap);			
		}
		
		if (isKey("clusterSize")) {
			int clusterSize = getInt(getNode("clusterSize"));
			if (clusterSize>0) spawn.clusterSizeMax=clusterSize;
		}
		
		if (isKey("clump")) spawn.clump = getBoolean(getNode("clump"));
		
		if (isKey("maxY")) spawn.yMax=getInt("maxY");
		if (isKey("minY")) spawn.yMin=getInt("minY");
		
		isDone=true;
		finish();
		return 1;
	}
	
	protected int doDungeonLoot() {
		LinkedList<DungeonLootHelper> loot = new LinkedList<DungeonLootHelper>();
		
		Map<JsonStringNode, JsonNode> map = jNode.getFields();
		for (Entry<JsonStringNode,JsonNode> e : map.entrySet()) {
			DungeonLootHelper l = getDungeonLoot(e.getKey().getText(), e.getValue().getText());
			if (l==null) {
				logDelayed("Failed to identify dungeon loot "+e.getKey().getText());
				return 0;
			} else loot.add(l);
		}
		
		if (!loot.isEmpty()) for (DungeonLootHelper l : loot) Xie.addDungeonLoot(l);
		return 1;
	}
	
	protected DungeonLootHelper getDungeonLoot(String item, String s) {
		ItemStack itemstack = Xie.parseItemStack(item);
		if (itemstack==null) return null;
		
		float rarity=1F;
		int min = 1;
		int max = 1;
		
		if (!s.isEmpty()) {
			if (s.contains("@")) {
				String ss[] = s.split("@");
				rarity = Float.parseFloat(ss[1]);
				s=ss[0];
			}
	
			if (s.contains("-")) {
				String ss[] = s.split("-");
				min = Integer.parseInt(ss[0]);
				s=ss[1];
			}
			
			max = Integer.parseInt(s);
		}
		
		return new DungeonLootHelper(itemstack, rarity, min, max);
	}

	protected void finish() {
		switch (type) {
		case surface: Spawnable.surface.add(spawn); break;
		case nether: Spawnable.nether.add(spawn); break;
		case dungeon: Spawnable.dungeon.add(spawn); break;
		case ore: Spawnable.ore.add(spawn); break;
		}
		
		logInfo("Spawnable added!");
	}

	protected Spawnable initSpawn() {
		Block b;
		int meta = -1;
		
		String blockName = name;
		if (isKey("block")) blockName = getStr("block");
		
		if (blockName.contains(":")) {
			String s[] = blockName.split(":");
			if (s.length>0) blockName=s[0];
			if (s.length>1) meta = Integer.parseInt(s[1]);
		}
		
		ItemWrapper iw = Xie.parseItemWrapper(blockName);
		if (iw==null) {
			logDelayed("Failed to find block named "+blockName);
			return null;
		}
		
		b = iw.getBlock();
		if (iw.hasMeta()) meta=iw.meta;
				
		if (b==null) {
			logDelayed("Failed to find block named "+blockName);
		}
		
		if (meta<0) return new Spawnable(b);
		else return new Spawnable(b,meta);
	}
}
