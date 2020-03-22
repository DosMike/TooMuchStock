# Too Much Stock

This plugin aims to provide an API for dynamic pricing based on trade volume and
popularity of items. 
In order to keep the Server economy within limits the following factors are put
in place:
* Trade volume per player; per item globally; per item and shop; per item and player in 
  * Limit of total sold items (disperseAmount)
  * Limit of total items purchased (purchaseAmount)
  * Limit of total income through trade per currency (incomeLimit)
  * Limit of total spedings through trande per currency (spendingLimit)
* Price modifications per item globally; per item and shop; per item and player with
  * Price multiplier for any sold item (dispersionDevaluation)
  * Price decay rate per item sold by a player
  * Price growth rate per item bought by a plaer
  * Discrapency returns over time with half life in minutes
  * Reset periods to prevent permanent damage to prices

Every aspect can be tuned and disabled through a config file. Most 
configurations can be done per item through item type / meta or nbt filters.

**Keep in mind that this plugin primarily tracks price increase**, this means
you can still make shops that sell items for more or less money (e.g. black markets)
but the prices still scale with demand and supply!

**Additional features:**
* Create Item aliases through a command, for use in per item configurations
* Visuall display global price history and player specific price history per item

## Commands & Permissions

* `/toomuchstock reload [--hard]` (Permission: toomuchstock.command.reload)  
  Reload the config. If --hard is specified, will also reset cooldowns.
* `/toomuchstock define <filter> <name>` (Permission: toomuchstock.command.define)  
  Hold an item to add it to the config under the specified name for per item configuration
* `/toomuchstock history [item]` (Permission: toomuchstock.command.stonks)  
  Hold an item or type the item name in the command to get a visual history of the item
  price (as multiplier to a base price) over the last 30 minutes.

## Example config

An example configuration with comments can be found [here](https://github.com/DosMike/TooMuchStock/blob/master/example.conf).

## Pricing API

The interface can be obtained as Service using PriceCalculationService:
```Java
@Listener public void onChangeServiceProvider(ChangeServiceProviderEvent event) {
	if (event.getService().equals(PriceCalculationService.class)) {
		pricingService = (PriceCalculationService) event.getNewProvider();
	}
}
```

The API provides current prices with   
`pricingService.getCurrentPurchasePrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID)`

As soon as a player shows interest in items and due to the exponential nature
of dynamic prices the next step would be to call   
`TransactionPreview preview = pricingService.getPurchaseInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID)`   
with the preview holding all price steps up to amount, and the amount of items 
the player can afford (to buy OR sell until they hit account limits).  
The Transaction should be finished with a call to preview.confirm like   
`preview.confirm(preview.getAffordableAmount())`   
to actually update the prices within the trackers.

Once a price changes through a transaction, discrapency decay or reset a 
PriceChangeEvent will be emitted for plugins to update their dispalys.

/* Note: I'll add an example on how to optionally use this plugin, 
so you do not have to depend on it */

**As Dependency**

```
repositories {
	...
	maven { url 'https://jitpack.io' }
}

dependencies {
	implementation 'com.github.DosMike:TooMuchStock:master-SNAPSHOT'
}
```
