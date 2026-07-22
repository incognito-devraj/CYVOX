package com.cyvox.animation;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

public final class UiAnimator {

    private static final Duration MOTION_DURATION = Duration.millis(240);

    private UiAnimator() {
    }

    public static void reveal(Node node) {
        node.setManaged(true);
        node.setVisible(true);
        node.setOpacity(0);
        node.setTranslateY(10);
        node.setScaleX(0.985);
        node.setScaleY(0.985);

        FadeTransition fade = new FadeTransition(MOTION_DURATION, node);
        fade.setToValue(1);
        TranslateTransition translate = new TranslateTransition(MOTION_DURATION, node);
        translate.setToY(0);
        ScaleTransition scale = new ScaleTransition(MOTION_DURATION, node);
        scale.setToX(1);
        scale.setToY(1);
        new ParallelTransition(fade, translate, scale).play();
    }

    public static void hide(Node node) {
        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setToValue(0);
        fade.setOnFinished(event -> {
            node.setVisible(false);
            node.setManaged(false);
        });
        fade.play();
    }

    public static void pulse(Node node) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(120), node);
        grow.setToX(1.025);
        grow.setToY(1.025);
        ScaleTransition settle = new ScaleTransition(Duration.millis(140), node);
        settle.setToX(1);
        settle.setToY(1);
        grow.setOnFinished(event -> settle.play());
        grow.play();
    }
}
