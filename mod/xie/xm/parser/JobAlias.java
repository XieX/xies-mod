package mod.xie.xm.parser;

import java.util.LinkedList;
import java.util.List;

import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.JobType;
import mod.xie.xm.util.Xie;




import argo.jdom.JsonNode;

public class JobAlias extends Job  {

	public JobAlias(String n, JsonNode node) {
		super(JobType.ALIAS, n, node);
	}

	@Override
	public int doWork() {
		logOpen();
		// TODO support types other than strings
		String strings[] = jNode.getText().split(",");
		List<ItemWrapper> itemList = new LinkedList();
		for (String s : strings) {
			ItemWrapper i = Xie.parseItemWrapper(s.trim());
			if (i==null) {
				logDelayed("Found unknown identifier: "+s);
				logClose();
				return 0;
			}
			itemList.add(i);
		}
		
		if (Xie.aliasMap.containsKey(name)) 
			Xie.aliasMap.get(name).addAll(itemList); 
		else Xie.aliasMap.put(name, itemList);
		
		isDone=true;
		logInfo(" ===  Alias Job done! === ");
		return 1;
	}
	
}
