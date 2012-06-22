/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.otto;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Test case for {@link Bus}.
 *
 * @author Cliff Biffle
 */
public class BusTest extends TestCase {
  private static final String EVENT = "Hello";
  private static final String BUS_IDENTIFIER = "test-bus";

  private Bus bus;

  @Override protected void setUp() throws Exception {
    super.setUp();
    bus = new Bus(ThreadEnforcer.ANY, BUS_IDENTIFIER);
  }

  public void testBasicCatcherDistribution() {
    StringCatcher catcher = new StringCatcher();
    bus.register(catcher);

    Set<EventHandler> wrappers = bus.getHandlersForEventType(String.class);
    assertNotNull("Should have at least one method registered.", wrappers);
    assertEquals("One method should be registered.", 1, wrappers.size());

    bus.post(EVENT);

    List<String> events = catcher.getEvents();
    assertEquals("Only one event should be delivered.", 1, events.size());
    assertEquals("Correct string should be delivered.", EVENT, events.get(0));
  }

  /**
   * Tests that events are distributed to any subscribers to their type or any
   * supertype, including interfaces and superclasses.
   *
   * Also checks delivery ordering in such cases.
   */
  public void testPolymorphicDistribution() {
    // Three catchers for related types String, Object, and Comparable<?>.
    // String isa Object
    // String isa Comparable<?>
    // Comparable<?> isa Object
    StringCatcher stringCatcher = new StringCatcher();

    final List<Object> objectEvents = new ArrayList<Object>();
    Object objCatcher = new Object() {
      @SuppressWarnings("unused")
      @Subscribe public void eat(Object food) {
        objectEvents.add(food);
      }
    };

    bus.register(stringCatcher);
    bus.register(objCatcher);

    // Two additional event types: Object and Comparable<?> (played by Integer)
    final Object OBJ_EVENT = new Object();
    final Object COMP_EVENT = new Integer(6);

    bus.post(EVENT);
    bus.post(OBJ_EVENT);
    bus.post(COMP_EVENT);

    // Check the StringCatcher...
    List<String> stringEvents = stringCatcher.getEvents();
    assertEquals("Only one String should be delivered.",
        1, stringEvents.size());
    assertEquals("Correct string should be delivered.",
        EVENT, stringEvents.get(0));

    // Check the Catcher<Object>...
    assertEquals("Three Objects should be delivered.",
        3, objectEvents.size());
    assertEquals("String fixture must be first object delivered.",
        EVENT, objectEvents.get(0));
    assertEquals("Object fixture must be second object delivered.",
        OBJ_EVENT, objectEvents.get(1));
    assertEquals("Comparable fixture must be thirdobject delivered.",
        COMP_EVENT, objectEvents.get(2));
  }

  public void testDeadEventForwarding() {
    GhostCatcher catcher = new GhostCatcher();
    bus.register(catcher);

    // A String -- an event for which noone has registered.
    bus.post(EVENT);

    List<DeadEvent> events = catcher.getEvents();
    assertEquals("One dead event should be delivered.", 1, events.size());
    assertEquals("The dead event should wrap the original event.",
        EVENT, events.get(0).getEvent());
  }

  public void testDeadEventPosting() {
    GhostCatcher catcher = new GhostCatcher();
    bus.register(catcher);

    bus.post(new DeadEvent(this, EVENT));

    List<DeadEvent> events = catcher.getEvents();
    assertEquals("The explicit DeadEvent should be delivered.",
        1, events.size());
    assertEquals("The dead event must not be re-wrapped.",
        EVENT, events.get(0).getEvent());
  }

  public void testProducerCalledForExistingSubscribers() {
    StringCatcher catcher = new StringCatcher();
    StringProducer producer = new StringProducer();

    bus.register(catcher);
    bus.register(producer);

    assertEquals(Arrays.asList(StringProducer.VALUE), catcher.getEvents());
  }

  public void testFlattenHierarchy() {
    HierarchyFixture fixture = new HierarchyFixture();
    Set<Class<?>> hierarchy = bus.flattenHierarchy(fixture.getClass());

    assertEquals(3, hierarchy.size());
    assertContains(Object.class, hierarchy);
    assertContains(HierarchyFixtureParent.class, hierarchy);
    assertContains(HierarchyFixture.class, hierarchy);
  }

  public void testMissingSubscribe() {
    bus.register(new Object());
  }

  public void testUnregister() {
    StringCatcher catcher1 = new StringCatcher();
    StringCatcher catcher2 = new StringCatcher();
    try {
      bus.unregister(catcher1);
      fail("Attempting to unregister an unregistered object succeeded");
    } catch (IllegalArgumentException expected) {
      // OK.
    }

    bus.register(catcher1);
    bus.post(EVENT);
    bus.register(catcher2);
    bus.post(EVENT);

    List<String> expectedEvents = new ArrayList<String>();
    expectedEvents.add(EVENT);
    expectedEvents.add(EVENT);

    assertEquals("Two correct events should be delivered.",
                 expectedEvents, catcher1.getEvents());

    assertEquals("One correct event should be delivered.",
        Arrays.asList(EVENT), catcher2.getEvents());

    bus.unregister(catcher1);
    bus.post(EVENT);

    assertEquals("Shouldn't catch any more events when unregistered.",
                 expectedEvents, catcher1.getEvents());
    assertEquals("Two correct events should be delivered.",
                 expectedEvents, catcher2.getEvents());

    try {
      bus.unregister(catcher1);
      fail("Attempting to unregister an unregistered object succeeded");
    } catch (IllegalArgumentException expected) {
      // OK.
    }

    bus.unregister(catcher2);
    bus.post(EVENT);
    assertEquals("Shouldn't catch any more events when unregistered.",
                 expectedEvents, catcher1.getEvents());
    assertEquals("Shouldn't catch any more events when unregistered.",
                 expectedEvents, catcher2.getEvents());
  }

  private <T> void assertContains(T element, Collection<T> collection) {
    assertTrue("Collection must contain " + element,
        collection.contains(element));
  }

  /**
   * A collector for DeadEvents.
   *
   * @author cbiffle
   *
   */
  public static class GhostCatcher {
    private List<DeadEvent> events = new ArrayList<DeadEvent>();

    @Subscribe
    public void ohNoesIHaveDied(DeadEvent event) {
      events.add(event);
    }

    public List<DeadEvent> getEvents() {
      return events;
    }
  }

  public interface HierarchyFixtureInterface {
    // Exists only for hierarchy mapping; no members.
  }

  public interface HierarchyFixtureSubinterface
      extends HierarchyFixtureInterface {
    // Exists only for hierarchy mapping; no members.
  }

  public static class HierarchyFixtureParent
      implements HierarchyFixtureSubinterface {
    // Exists only for hierarchy mapping; no members.
  }

  public static class HierarchyFixture extends HierarchyFixtureParent {
    // Exists only for hierarchy mapping; no members.
  }

}
