Feature: Invoice difference causes
  So that a customer understands why one bill differs from the previous one, the
  assistant names each confirmed cause with the computed amount. It transfers
  the customer only when the change cannot be explained.

  Scenario: A fibre customer with a new TV offer hears the add-on, the new discount and the activation fee
    Given the eir customer "99226126"
    And the assistant repeats the computed explanation
    When the customer asks in French why their bill changed
    Then the customer hears "66.51"
    And the customer hears "41.31"
    And the customer hears "7.83"
    And the customer hears "99.99"
    And the customer hears "nouveau service"
    And the customer is not transferred to an advisor

  Scenario: The same fibre customer hears the same causes in English
    Given the eir customer "99226126"
    And the assistant repeats the computed explanation
    When the customer asks in English why their bill changed
    Then the customer hears "66.51"
    And the customer hears "new service"
    And the customer hears "start of a discount"
    And the customer hears "activation fee"
    And the customer is not transferred to an advisor

  Scenario: A mobile customer hears the new options and the one-off bundle
    Given the eir customer "99224964"
    And the assistant repeats the computed explanation
    When the customer asks in French why their bill changed
    Then the customer hears "55.47"
    And the customer hears "25.48"
    And the customer hears "29.99"
    And the customer hears "bundle ponctuel"
    And the customer is not transferred to an advisor

  Scenario: A fibre and TV customer hears the partial period and the new mobile subscription
    Given the eir customer "99226337"
    And the assistant repeats the computed explanation
    When the customer asks in French why their bill changed
    Then the customer hears "34.23"
    And the customer hears "101.82"
    And the customer hears "1.94"
    And the customer hears "15.66"
    And the customer hears "49.99"
    And the customer does not hear "fin d'une remise"
    And the customer is not transferred to an advisor

  Scenario: An unknown customer reference is handed to an advisor without an amount
    Given the eir customer "00000000"
    When the customer asks in French why their bill changed
    Then the customer is transferred to an advisor
    And the answer contains no amount
    And the language model is not called

  Scenario: A question that is not about the bill does not speak invoice causes
    Given the eir customer "99226126"
    When the customer asks how to set up their router
    Then the customer is not transferred to an advisor
    And the language model is not called
    And the customer does not hear "41.31"
