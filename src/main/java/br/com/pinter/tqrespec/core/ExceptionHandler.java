/*
 * Copyright (C) 2021 Emerson Pinter - All Rights Reserved
 */

/*    This file is part of TQ Respec.

    TQ Respec is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    TQ Respec is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with TQ Respec.  If not, see <http://www.gnu.org/licenses/>.
*/

package br.com.pinter.tqrespec.core;

import br.com.pinter.tqrespec.logging.Log;
import br.com.pinter.tqrespec.util.Build;
import br.com.pinter.tqrespec.util.Constants;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Optional;

public class ExceptionHandler {
    private static final System.Logger logger = Log.getLogger(ExceptionHandler.class.getName());

    private ExceptionHandler() {
    }

    @SuppressWarnings("unused")
    public static void unhandled(Thread t, Throwable e) {
        logger.log(System.Logger.Level.ERROR, Constants.ERROR_MSG_EXCEPTION, e);

        if (Platform.isFxApplicationThread()) {
            ExceptionHandler.showAlert(t, e);
        }
    }

    @SuppressWarnings("unused")
    private static void showAlert(Thread t, Throwable e) {
        String header = ExceptionUtils.getRootCause(e).toString();
        if (header == null) {
            header = e.toString();
        }
        header = header.replaceAll("^java.lang.RuntimeException: (.*)", "$1");
        header = header.replaceAll("^br.com.pinter.tqrespec.core.UnhandledRuntimeException: (.*)", "$1");
        header = header.replaceAll("^br.com.pinter.tqrespec.(.*)", "$1");

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle(Build.title());
        alert.setHeaderText("An unhandled exception occurred");
        alert.setContentText(header);

        TextArea textArea = new TextArea(ExceptionUtils.getStackTrace(e.getCause()));
        textArea.setEditable(false);
        textArea.setWrapText(false);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(expContent);

        ButtonType abort = new ButtonType("Abort");
        alert.getButtonTypes().setAll(abort);


        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == abort) {
            Platform.exit();
            System.exit(0);
        }
    }


}
