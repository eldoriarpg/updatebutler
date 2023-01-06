package de.eldoria.updatebutler.api.verify;

import io.javalin.Javalin;

public class Verify {
    public Verify(Javalin javalin) {
        javalin.get("/verify/:id", ctx -> {
            ctx.result("""
                                <html>
                                   <body>
                                        <button id="myButton" class="float-left submit-button" >Home</button>
                                   
                                        <script type="text/javascript">
                                            document.getElementById("myButton").onclick = function () {
                                            location.href = "www.yoursite.com";
                                            };
                                        </script>
                                   </body>
                                </html>   
                       """);
        })
    }
}
