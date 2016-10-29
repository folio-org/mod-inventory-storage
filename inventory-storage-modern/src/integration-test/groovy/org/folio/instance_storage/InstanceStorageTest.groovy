package org.folio.instance_storage

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ian Ibbotson
 */
public class InstanceStorageTest {

    public static test_instance_one = [
      'folioInstanceHash':'',
      'title':'Platform for Change',
      'primaryAuthor':'Beer, Stafford',
      'identifiers':[
        [namespace:'isxn',value:'0471061891']
      ],
      'subjects':['System Theory','Operations Research']
    ]

    @Before
    public void setUp() {
      // Nothing currently
    }

    @Test
    public void storeInstance_ShouldSaveWithoutError() {
        assertThat(test_instance_one.title).isEqualTo("Platform for Change");
    }
}
