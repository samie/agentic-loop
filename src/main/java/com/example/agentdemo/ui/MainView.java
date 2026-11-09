package com.example.agentdemo.ui;

import com.example.agentdemo.ModelService;
import com.example.agentdemo.ui.architecture.ArchitectureView;
import com.example.agentdemo.ui.demo.DemoView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import java.util.Map;

@Route("")
@PageTitle("Agentic loop demo")
public class MainView extends VerticalLayout implements BeforeEnterObserver {

    private static final String TAB = "tab";
    private static final String STEP = "step";
    private static final String ARCHITECTURE = "architecture";

    private final TabSheet tabs = new TabSheet();
    private final ArchitectureView architecture = new ArchitectureView();
    private final Tab demoTab;
    private final Tab architectureTab;

    public MainView(ModelService models) {
        addClassName("main-view");
        tabs.setWidthFull();
        demoTab = tabs.add("Demo", new DemoView(models));
        architectureTab = tabs.add("Architecture", architecture);
        tabs.addSelectedChangeListener(e -> {
            if (e.isFromClient()) keepTheSelectedTabInTheUrl();
        });
        add(new H2("Agentic loop demo"), tabs);
    }

    public static void showArchitecture(LoopStage stage) {
        UI.getCurrent().navigate(MainView.class,
                QueryParameters.simple(Map.of(TAB, ARCHITECTURE, STEP, stage.diagramKey())));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters query = event.getLocation().getQueryParameters();
        boolean architectureRequested = query.getSingleParameter(TAB).filter(ARCHITECTURE::equals).isPresent();
        tabs.setSelectedTab(architectureRequested ? architectureTab : demoTab);
        query.getSingleParameter(STEP).flatMap(LoopStage::fromDiagramKey).ifPresent(architecture::showStep);
    }

    private void keepTheSelectedTabInTheUrl() {
        QueryParameters query = tabs.getSelectedTab() == architectureTab
                ? QueryParameters.of(TAB, ARCHITECTURE)
                : QueryParameters.empty();
        UI.getCurrent().navigate(MainView.class, query);
    }
}
