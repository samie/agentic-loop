package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopStage;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;

public class StageBadge extends Composite<Badge> {

    private final LoopStage stage;
    private final Popover whereInTheLoop = new Popover();

    public StageBadge(LoopStage stage) {
        this.stage = stage;
        Badge badge = getContent();
        badge.setText(stage.badge());
        badge.addClassName("stage-badge");
        badge.getElement().setAttribute("tabindex", "0");
        badge.getElement().setAttribute("aria-label",
                stage.badge() + " " + stage.title() + ": show where this step is in the loop");

        whereInTheLoop.setTarget(badge);
        whereInTheLoop.setOpenOnHover(true);
        whereInTheLoop.setOpenOnFocus(true);
        whereInTheLoop.setOpenOnClick(true);
        whereInTheLoop.setPosition(PopoverPosition.BOTTOM_START);
        whereInTheLoop.setWidth("640px");
        whereInTheLoop.setAriaLabel(stage.badge() + " " + stage.title() + " in the agentic loop");
        whereInTheLoop.addOpenedChangeListener(e -> {
            if (e.isOpened()) showTheOverviewOnFirstOpen();
        });
    }

    @Override
    protected Badge initContent() {
        return new Badge();
    }

    private void showTheOverviewOnFirstOpen() {
        if (whereInTheLoop.getChildren().findAny().isPresent()) return;
        whereInTheLoop.add(new StageOverview(stage, whereInTheLoop::close));
    }
}
