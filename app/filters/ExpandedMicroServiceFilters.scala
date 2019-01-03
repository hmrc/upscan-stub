package filters

import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter
import uk.gov.hmrc.play.bootstrap.filters.MicroserviceFilters

class ExpandedMicroServiceFilters @Inject()(
                        microserviceFilters: MicroserviceFilters,
                        corsFilter: CORSFilter)
  extends DefaultHttpFilters(microserviceFilters.filters :+ corsFilter: _*)
