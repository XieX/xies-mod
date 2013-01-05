package xie.mods.xm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import xie.mods.xm.file.PathManager;
import xie.mods.xm.file.SpritesheetManager;
import xie.mods.xm.file.XieFormatter;
import xie.mods.xm.file.XieLevel;
import xie.mods.xm.file.XieLog;
import xie.mods.xm.parser.Job;
import xie.mods.xm.parser.JobAlias;
import xie.mods.xm.parser.JobBlock;
import xie.mods.xm.parser.JobItem;
import xie.mods.xm.parser.JobRecipe;
import xie.mods.xm.parser.JobSpawn;
import xie.mods.xm.util.ItemWrapper;
import xie.mods.xm.util.JobType;
import xie.mods.xm.util.Xie;
import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;
import argo.jdom.JsonRootNode;
import argo.jdom.JsonStringNode;
import argo.saj.InvalidSyntaxException;


public class Work {	
	private static final String cssStyleFile = "xielog.css";
	
	// Work orders - ordered list of things to be initialized
	private LinkedList<Job> jobs;
	private LinkedList<Job> jobsCopy;
	public Map<String,Integer> ids;
	
	public int[] jobsCompleted;
	public int[] jobsFound;
	
	// Initialization, run once though called by each Xie mod
	private File langFile;
	private Properties lang;
	
	protected final static int ALIAS = -7; // code for alias
	
	private static Work instance;
	
	public static Work instance() {
		if (instance==null) instance = new Work();
		return instance;
	}
	
	private Work() {
		jobs = new LinkedList();
		jobsCopy = new LinkedList();
		
		jobsCompleted = new int[JobType.values().length];
		jobsFound = new int[JobType.values().length];
		
		ids = new HashMap<String,Integer>();
	}
	
	public void resetJobQueue() {
		purgeXiesModContent();
		jobs=new LinkedList();
	}
	
	private List<Integer> itemsAdded = new LinkedList();
	private List<Integer> blocksAdded = new LinkedList();
	
	private void purgeXiesModContent() {
		for (int i : itemsAdded) {
			Item.itemsList[i]=null;
		}
		for (int i : blocksAdded) {
			Item.itemsList[i]=null;
			Block.blocksList[i]=null;
		}
	}
	
	public void blockAdded(int i) {
		blocksAdded.add(i);
	}
	
	public void itemAdded(int i) {
		itemsAdded.add(i);
	}
	
	public void findWork(File file) throws InvalidSyntaxException, IOException {
		findWork(new BufferedReader(new FileReader(file)), file.getParent(), file.getName());
	}

	public void findWork(InputStream inputStream, String path, String name) throws InvalidSyntaxException, IOException {
		findWork(new InputStreamReader(inputStream),path,name);
	}
	
	// TODO wtf are you doing here?
	public void findWork(Reader reader, String path, String filename) throws InvalidSyntaxException, IOException {
		path = path.replace("\\","/");
		
		if (filename!=null) {
			path = PathManager.get().localiseToContent(path);
		}
			
		if (filename!=null)
			if (filename.contains(Job.X)) filename = filename.substring(0,filename.indexOf(Job.X));
		
		JsonRootNode rootNode = new JdomParser().parse(reader);
		
		Map<JsonStringNode, JsonNode> map = rootNode.getFields();
		
		for (JsonNode key : map.keySet()) {
			// first check to handle either "ids" or "texturePaths" keys
			if (key.getText().equals("ids")) {
				handleIds(map.get(key));
				continue;
			}
			
			if (key.getText().equals("texturePaths")) {
				handleTexturePaths(map.get(key));
				continue;
			}
			
			// check for legit job type key ("blocks", "items" etc...)
			JobType t = Job.getType(key.getText());
			if (t==null) {
				// check to see if we don't care about this invalid job type
				if (key.getText().startsWith("_")) continue;
				if (key.getText().equals("version")) continue; // TODO implement version checking
				// log it and move on
				XieLog.log().logIncomplete("Invalid job type "+key.getText());
				continue;
			}
			
			// process job type key and add it to job queue
			JsonNode subtype = map.get(key);
			Map<JsonStringNode, JsonNode> subtypeMap = subtype.getFields();
			
			for (JsonNode subkey : subtypeMap.keySet()) {
				// add these elements to the work queue
				String name = subkey.getText();
				JsonNode node = subtypeMap.get(subkey);
				if (path.contains("\\config\\Xie\\content"))
					path = "."+path.substring(path.indexOf("\\config\\Xie\\content"));
				else if (path.contains("/config/Xie/content")) {
					path = "."+path.substring(path.indexOf("/config/Xie/content"));
					path = path.replace("/", "\\");
				}
				if (filename!=null)
					addJob(t, filename+Job.X+name, node, path);
				else addJob(t,name,node,path);
			}
		}
		
		reader.close();
	}
	
	private void handleTexturePaths(JsonNode jsonNode) {
		Map<JsonStringNode, JsonNode> map = jsonNode.getFields();
		for (Entry<JsonStringNode,JsonNode> e : map.entrySet()) {
			
			String filepath = PathManager.get().cacheDir() + e.getValue().getText();
			SpritesheetManager.get().addTexture(e.getKey().getText(), filepath);
		}
	}
	
	private void handleIds(JsonNode idsNode) {
		Map<JsonStringNode, JsonNode> idsMap = idsNode.getFields();
		for (Entry<JsonStringNode,JsonNode> e : idsMap.entrySet()) {
			String identifier = e.getKey().getText();
			if (e.getValue().getType()==JsonNodeType.NUMBER) {
				addId(e.getKey().getText(),e.getValue().getText());
			} else XieLog.log().logIncomplete("Invalid id for identifier "+identifier+" - integer expected.");
		}
	}
	
	public void findIDs(InputStream i) throws IOException, InvalidSyntaxException {
		findIDs(new InputStreamReader(i));
	}
	
	// processes a properties file for block and item id declarations
	public void findIDs(Reader r) throws IOException, InvalidSyntaxException {
		BufferedReader reader = new BufferedReader(r);
		
		for (String line = reader.readLine(); line!=null; line=reader.readLine()) {
			// ignore whitespace and comments
			if (!line.matches("[^#]\\S+")) continue;
						
			String s[] = line.split("=");
			if (s.length<2) {
				// log invalid string
				XieLog.log().logIncomplete("Invalid line in ids file, expected identifier=id: "+line);
				continue;
			}
			addId(s[0],s[1]);
		}
		
		reader.close();
	}
	
	private void addId(String identifier, String idText) {
		try {
			int id = Integer.parseInt(idText);
			ids.put(identifier, id);
		} catch (NumberFormatException exception) {
			XieLog.log().logIncomplete("Invalid id for identifier "+identifier+" - integer expected.");
		}
	}
	
	public int getIdFor(String s) {
		if (ids.containsKey(s)) return ids.get(s);
		
		for (String key : ids.keySet())
			if (key.equalsIgnoreCase(s)) return ids.get(key);
		
		return -1;
	}
	
	public void addJob(JobType t, String name, JsonNode node, String path) {
		jobsFound[t.ordinal()]++;
		switch(t) {
		case BLOCK: jobs.add(new JobBlock(name, node, path)); break;
		case ITEM: jobs.add(new JobItem(name, node, path)); break;
		case RECIPE: jobs.add(new JobRecipe(name, node)); break;
		case SPAWN: jobs.add(new JobSpawn(name, node)); break;
		case ALIAS: jobs.add(new JobAlias(name, node)); break;
		}
	}
	
	public void addSpawnJob(String name, JsonNode node) {
		jobs.add(new JobSpawn(name,node));
	}

	// attempts to resolve the work queue
	private static final int MAX_PASSES = 20;
    public Boolean doWork() {
        for(int passCounter = 0; passCounter < MAX_PASSES && !jobs.isEmpty(); passCounter++) {
            XieLog.log().fine((new StringBuilder()).append("Attempting pass ").append(passCounter).append(" of job queues").toString());
            resolveJobList(jobs);
        }

        return jobs.isEmpty();
    }
    
    public void logIncompletes() {
    	for (Job j : jobs) {
    		if (!j.isFail) j.logAsIncomplete();
    	}
    }
	
	private Boolean resolveJobList(LinkedList<Job> jobs) {
		if (jobs.isEmpty()) return true;
		
		for (int i=0; i<jobs.size(); i++) {
			Job job = jobs.pop();
	
			// process current job
			int count = 0;
			if (!job.isFail) count = job.doWork();
			
			// if job could not be completed, bump it to the end of the queue
			if (!job.isDone()) jobs.add(job);
			else jobsCompleted[job.type.ordinal()]+=count;
		}

		return jobs.isEmpty();
	}
	
	public String[] getFailMsgs() {
		if (jobs.isEmpty()) return new String[] {"All jobs successfully resolved!"};
		String failMsgs[] = new String[jobs.size()];
		int i=0;
		for (Job job : jobs) {
			failMsgs[i++]=job.getDescriptor()+" "+job.getFailReason();
		}
		return failMsgs;
	}

	// DEBUG
	public String dumpAll() {
		int count = jobs.size();
		String msg = count+" jobs:\n";
		
		for (Job job : jobs) msg+=job.getDescriptor()+" "+job.getFailReason()+"\n";
		
		return msg;
	}
	
	public String dumpWorkOrder(LinkedList<Job> jobs) {
		String dump = "";
		Iterator<Job> it = jobs.iterator();
		while (it.hasNext()) {
			dump+=it.next().toString();
			dump+="\n";
		}
		return dump;
	}
	
	public void backupJobsList() {
		jobsCopy = new LinkedList<Job>();
		
		if (jobs.isEmpty()) return;
		
		for (Job j : jobs) {
			jobsCopy.add(j);
		}
	}
}