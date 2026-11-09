package com.example.agentdemo.ui.architecture;

import com.example.agentdemo.ui.LoopStage;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.ScrollIntoViewOption;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.markdown.Markdown;
import java.util.EnumMap;
import java.util.Map;

public class LoopStepsList extends Composite<OrderedList> {

    private final Map<LoopStage, ListItem> items = new EnumMap<>(LoopStage.class);

    public LoopStepsList() {
        getContent().addClassName("steps");
        for (LoopStage stage : LoopStage.values()) {
            ListItem item = new ListItem(new Markdown(
                    "**" + stage.badge() + " " + stage.title() + ".** " + stage.explanationMarkdown()));
            items.put(stage, item);
            getContent().add(item);
        }
    }

    public void showStep(LoopStage stage) {
        items.forEach((each, item) -> item.setClassName("current-step", each == stage));
        items.get(stage).scrollIntoView(ScrollIntoViewOption.Block.CENTER);
    }
}
