package org.ohnlp.medtagger.backbone;

import org.apache.beam.sdk.coders.BigEndianLongCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.RowCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.transforms.Select;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.joda.time.Duration;
import org.ohnlp.backbone.api.annotations.ComponentDescription;
import org.ohnlp.backbone.api.components.OneToOneTransform;
import org.ohnlp.backbone.api.exceptions.ComponentInitializationException;
import org.ohnlp.medtagger.lvg.LvgLookup;

@ComponentDescription(
        name = "Get Dict Freqs",
        desc = "Gets Frequency of Dictionary Terms (Useful for Cleaning Noise from Autogenerated Dictionary Entries)"
)
public class CleanMedTaggerDictOutputTransform extends OneToOneTransform {
    private final Schema schema = Schema.of(
            Schema.Field.of("matched_text", Schema.FieldType.STRING),
            Schema.Field.of("freq", Schema.FieldType.INT64)
    );

    @Override
    public Schema calculateOutputSchema(Schema schema) {
        return this.schema;
    }

    @Override
    public PCollection<Row> expand(PCollection<Row> input) {
        return input.apply(Select.fieldNames("matched_text", "note_source_value")).apply(ParDo.of(
                new DoFn<Row, KV<Row, String>>() {
                    private LvgLookup lvg;
                    @ProcessElement
                    public void process(ProcessContext pc) {
                        Row input = pc.element();
                        String text = input.getString("matched_text");
                        text = lvg.getNorm(text).replaceAll("\\s", "\t");
                        pc.output(KV.of(Row.withSchema(schema).addValues(text, 1L).build(), input.getString("note_source_value")));
                    }
                    @Setup
                    public void init() {
                        this.lvg = new LvgLookup();
                        lvg.localInitialize(CleanMedTaggerDictOutputTransform.class.getResourceAsStream("/medtaggerresources/lvg/LRAGR_2021AB"), CleanMedTaggerDictOutputTransform.class.getResourceAsStream("/medtaggerresources/lvg/openclasswords.txt"));
                    }
                }
        )).setCoder(KvCoder.of(RowCoder.of(this.schema), StringUtf8Coder.of())
        ).apply(Distinct.create()
        ).apply(Count.perKey()
        ).setCoder(KvCoder.of(RowCoder.of(this.schema), BigEndianLongCoder.of())
        ).apply(MapElements.via(new SimpleFunction<KV<Row, Long>, Row>() {
            @Override
            public Row apply(KV<Row, Long> input) {
                return Row.withSchema(schema).addValues(input.getKey().getValue("matched_text"), input.getValue()).build();
            }
        }));
    }

    @Override
    public void init() throws ComponentInitializationException {
    }
}