package com.example.agentdemo.ui.demo;

import com.example.agentdemo.ui.LoopRole;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

public class RoleLegend extends Composite<Div> {

    public RoleLegend() {
        getContent().addClassName("legend");
        for (LoopRole role : LoopRole.LEGEND) {
            Span chip = new Span(role.legendLabel());
            chip.addClassNames("legend-chip", role.className());
            getContent().add(chip);
        }
    }
}
