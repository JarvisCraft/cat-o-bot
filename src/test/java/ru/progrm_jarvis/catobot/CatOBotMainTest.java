package ru.progrm_jarvis.catobot;

import com.vk.api.sdk.objects.messages.Message;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatOBotMainTest {

    @Test
    void testConfigMessageToCatImagesCountFunction() {
        val function = CatOBotMain.Config
                .builder()
                .catAlias("cat")
                .catAlias("kitty")
                .build()
                .createMessageToCatImagesCountFunction();

        val message = new Message();
        assertEquals(0, function.apply(message.setText("hello")));

        assertEquals(1, function.apply(message.setText("cat")));
        assertEquals(1, function.apply(message.setText("give me a cat")));
        assertEquals(1, function.apply(message.setText("give me some cats")));
        assertEquals(1, function.apply(message.setText("cats please")));
        assertEquals(1, function.apply(message.setText("give me some cats please")));

        assertEquals(2, function.apply(message.setText("2 cats")));
        assertEquals(7, function.apply(message.setText("give me please 7 cats")));
        assertEquals(4, function.apply(message.setText("4 cats please")));
        assertEquals(8, function.apply(message.setText("please, 8 cats")));
    }
}