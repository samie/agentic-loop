package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;

public class ObservationCard extends LoopCard {

    public ObservationCard() {
        super(LoopRole.TOOL, LoopStage.OBSERVATION, "Observation: the result goes back into the conversation", "");
        explain("It is added as a tool message, linked to the call, and sent with the next request.");
        compact();
    }
}
