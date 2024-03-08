package de.openinc.ow.plugin.chirp;

import static io.javalin.apibuilder.ApiBuilder.post;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.OpenWareAPI;
import de.openinc.api.OpenWarePlugin;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.helper.HTTPResponseHelper;
import de.openinc.ow.middleware.services.DataService;

public class Main implements OpenWarePlugin, OpenWareAPI {

	boolean active = false;

	@Override
	public void registerRoutes() {
		// TODO Auto-generated method stub
		post("/chirp/push/{source}", ctx -> {

			User user = null;
			OpenWareInstance.getInstance()
					.logTrace(String.format("[%s] %s)", this.getClass().getCanonicalName(), ctx.body()));
			String source = ctx.pathParam("source");
			if (Config.getBool("accessControl", true)) {
				user = ctx.sessionAttribute("user");
				if (user == null) {
					HTTPResponseHelper.forbidden("Provide Authorization header or 'OD-SESSION' header to authorize");

				}

			}
			try {
				JSONObject payload = new JSONObject(ctx.body());
				OpenWareInstance.getInstance()
						.logTrace("payload = "+payload);
				JSONObject decodedFields = payload.getJSONObject("object");
				double rssi = payload.getJSONArray("rxInfo").getJSONObject(0).optDouble("rssi", 0);
				JSONObject loraIds = payload.getJSONObject("deviceInfo");
				loraIds.put("devHash", "_" + loraIds.getString("devEui"));

				String name = loraIds.getString("applicationId") + "_"
						+ loraIds.getString("devEui");
				String id = loraIds.getString("applicationId") + "_"
						+ loraIds.getString("devEui");

//				if (decodedFields.has("idSuffix")) {
//					id = id + decodedFields.getString("idSuffix");
//					decodedFields.remove("idSuffix");
//				}

//				if (decodedFields.has("latitude") || decodedFields.has("lat")) {
//					if (decodedFields.has("longitude") || decodedFields.has("lon")) {
//						String template = "{\r\n" + "      \"type\": \"Feature\",\r\n" + "      \"geometry\": {\r\n"
//								+ "        \"type\": \"Point\",\r\n" + "        \"coordinates\": [%s, %s, %s]\r\n"
//								+ "      },\r\n" + "      \"properties\": {\r\n" + "        \"name\": %s,\r\n"
//								+ "        \"rssi\": %s\r\n" + "        \r\n" + "      }\r\n" + "    }   ";
//						double lon = decodedFields.has("lon") ? decodedFields.getDouble("lon")
//								: decodedFields.getDouble("longitude");
//						double lat = decodedFields.has("lat") ? decodedFields.getDouble("lat")
//								: decodedFields.getDouble("latitude");
//						double alt = decodedFields.has("alt") ? decodedFields.getDouble("alt") : 0.0;
//						if (alt == 0.0) {
//							alt = decodedFields.has("altitude") ? decodedFields.getDouble("altitude") : 0.0;
//						}
//						String geo = String.format(template, lon, lat, alt, name, rssi);
//						JSONObject geoJson = new JSONObject(geo);
//						decodedFields.remove("latitude");
//						decodedFields.remove("lat");
//						decodedFields.remove("lon");
//						decodedFields.remove("longitude");
//						decodedFields.remove("alt");
//						decodedFields.remove("altitude");
//						decodedFields.put("parsedGeo", geoJson);
//					}
//				}
				ArrayList<OpenWareValueDimension> vTypes = new ArrayList();
				OpenWareValue val = new OpenWareValue(System.currentTimeMillis());
				decodedFields.keySet().stream().sorted().forEachOrdered(new Consumer<String>() {
					@Override
					public void accept(String key) {
						if (key.endsWith("_unit"))
							return;
						OpenWareValueDimension dim = OpenWareValueDimension.inferDimension(decodedFields.get(key),
								decodedFields.optString(key + "_unit"), key);
						val.add(dim);
						vTypes.add(dim);
					}
				});
				if (!user.canAccessWrite(source, id)) {
					HTTPResponseHelper.forbidden("You do not have permission to write this data source");

				}
				OpenWareDataItem item = new OpenWareDataItem(id, source, name, loraIds, vTypes);
				item.value().add(val);
				DataService.onNewData(item);
				HTTPResponseHelper.ok(ctx, item);

			} catch (JSONException e) {
				OpenWareInstance.getInstance().logError("Malformed data posted to ChirpStack Integration API\n" + ctx.body(),
						e);
				HTTPResponseHelper.badRequest("Malformed data posted to ChirpStack-LORA API\n" + ctx.body());

			}

		});

	}

	@Override
	public boolean init(OpenWareInstance instance, JSONObject options) throws Exception {
		if (options != null) {
			active = options.optBoolean("enabled");
		}
		if (active) {
			instance.registerService(this);
		}

		return true;
	}

	@Override
	public boolean disable() throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

}
