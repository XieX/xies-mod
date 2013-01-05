package xie.mods.xm.util;

import java.util.LinkedList;
import java.util.List;

public class ContentPackage {

	private List<XieContent> content;
	
	public ContentPackage() {
		content = new LinkedList<XieContent>();
	}
	
	public void apply() {
		if (content.isEmpty()) return;
		for (XieContent x : content) {
			x.apply();
		}
	}
	
	public void undo() {
		if (content.isEmpty()) return;
		for (XieContent x : content) {
			x.undo();
		}
	}
	
	public void add(XieContent x) {
		content.add(x);
	}
	
	public void remove(XieContent x) {
		content.remove(x);
	}
}
