package pl.commercelink.shipping.furgonetka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.rest.client.BinaryResponse;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.Label;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FurgonetkaLabelTest {

    @Mock
    private RestApiWithRetry restApi;

    @Test
    void pdfLabelKeepsItsBytesAndGetsAPdfName() {
        // given
        byte[] pdf = {'%', 'P', 'D', 'F'};
        when(restApi.fetchBytesWithAuthRetry(eq("/packages/21480003/label"), anyMap(), eq("application/pdf, text/plain")))
                .thenReturn(new BinaryResponse(pdf, "application/pdf"));

        // when
        Label label = new Furgonetka(restApi).getLabel("21480003");

        // then
        assertArrayEquals(pdf, label.content());
        assertEquals("application/pdf", label.contentType());
        assertEquals("etykieta-21480003.pdf", label.fileName());
    }

    @Test
    void zplLabelGetsAZplName() {
        // given
        when(restApi.fetchBytesWithAuthRetry(eq("/packages/21480003/label"), anyMap(), eq("application/pdf, text/plain")))
                .thenReturn(new BinaryResponse("^XA^XZ".getBytes(), "text/plain; charset=utf-8"));

        // when
        Label label = new Furgonetka(restApi).getLabel("21480003");

        // then
        assertEquals("etykieta-21480003.zpl", label.fileName());
        assertTrue(new Furgonetka(restApi).supportsLabels());
    }
}
