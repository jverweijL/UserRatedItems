package userrateditems;


import com.liferay.asset.kernel.AssetRendererFactoryRegistryUtil;
import com.liferay.portal.kernel.search.*;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleLoader;
import com.liferay.ratings.kernel.model.RatingsEntry;
import com.liferay.ratings.kernel.service.RatingsEntryLocalService;
import org.osgi.service.component.annotations.Component;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.persistence.AssetEntryQuery;
import com.liferay.asset.util.AssetHelper;
import com.liferay.info.list.provider.InfoListProvider;
import com.liferay.info.list.provider.InfoListProviderContext;

import com.liferay.info.pagination.Pagination;
import com.liferay.info.sort.Sort;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.User;

import java.util.*;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


/**
 * @author jverweij
 */
@Component(
	immediate = true,
	property = {
		// TODO enter required service properties
	},
	service = InfoListProvider.class
)
public class UserRatedAssetsInfoListProvider implements InfoListProvider<AssetEntry> {

	@Override
	public List<AssetEntry> getInfoList(
			InfoListProviderContext infoListProviderContext) {

		return getInfoList(infoListProviderContext, null, null);
	}

	@Override
	public List<AssetEntry> getInfoList(
			InfoListProviderContext infoListProviderContext, Pagination pagination,
			Sort sort) {

		AssetEntryQuery assetEntryQuery = getAssetEntryQuery(
				infoListProviderContext, Field.MODIFIED_DATE, "DESC", pagination);

		try {
			Hits hits = _assetHelper.search(
					_getSearchContext(infoListProviderContext), assetEntryQuery,
					assetEntryQuery.getStart(), assetEntryQuery.getEnd());

			// time to check whether it's rated/liked or not
			User user = infoListProviderContext.getUser();
			List<AssetEntry> assets = _assetHelper.getAssetEntries(hits);

			int i = 0;
			for (Document doc:hits.getDocs()) {
				RatingsEntry rating = _RatingsEntryLocalService.fetchEntry(user.getUserId(), doc.getField("entryClassName").getValue(), Long.valueOf(doc.getField("entryClassPK").getValue()));
				if (rating != null ) {
					//System.out.println("rating" + rating.getScore());
				} else {
					assets.remove(i);
				}
				i = i++;
			}

			return assets;
		}
		catch (Exception exception) {
			_log.error("Unable to get asset entries", exception);
		}

		return Collections.emptyList();
	}

	@Override
	public int getInfoListCount(
			InfoListProviderContext infoListProviderContext) {

		try {
			Long count = _assetHelper.searchCount(
					_getSearchContext(infoListProviderContext),
					getAssetEntryQuery(
							infoListProviderContext, Field.MODIFIED_DATE, "DESC",
							null));

			return count.intValue();
		}
		catch (Exception exception) {
			_log.error("Unable to get asset entries count", exception);
		}

		return 0;
	}

	@Override
	public String getLabel(Locale locale) {
		ResourceBundle resourceBundle =
				_resourceBundleLoader.loadResourceBundle(locale);

		return LanguageUtil.get(resourceBundle, "user-rated-content");
	}

	private SearchContext _getSearchContext(
			InfoListProviderContext infoListProviderContext)
			throws Exception {

		Company company = infoListProviderContext.getCompany();

		long groupId = company.getGroupId();

		Optional<Group> groupOptional =
				infoListProviderContext.getGroupOptional();

		if (groupOptional.isPresent()) {
			Group group = groupOptional.get();

			groupId = group.getGroupId();
		}

		User user = infoListProviderContext.getUser();

		Optional<Layout> layoutOptional =
				infoListProviderContext.getLayoutOptional();

		SearchContext searchContext = SearchContextFactory.getInstance(
				new long[0], new String[0], new HashMap<>(), company.getCompanyId(),
				null, layoutOptional.orElse(null), null, groupId, null,
				user.getUserId());

		searchContext.setSorts(
				SortFactoryUtil.create(
						Field.MODIFIED_DATE,
						com.liferay.portal.kernel.search.Sort.LONG_TYPE, true),
				SortFactoryUtil.create(
						Field.CREATE_DATE,
						com.liferay.portal.kernel.search.Sort.LONG_TYPE, true));

		return searchContext;
	}

	protected AssetEntryQuery getAssetEntryQuery(
			InfoListProviderContext infoListProviderContext, String orderByCol,
			String orderByType, Pagination pagination) {

		AssetEntryQuery assetEntryQuery = new AssetEntryQuery();

		Company company = infoListProviderContext.getCompany();

		long[] availableClassNameIds =
				AssetRendererFactoryRegistryUtil.getClassNameIds(
						company.getCompanyId(), true);

		availableClassNameIds = ArrayUtil.filter(
				availableClassNameIds,
				availableClassNameId -> {
					Indexer<?> indexer = IndexerRegistryUtil.getIndexer(
							portal.getClassName(availableClassNameId));

					if (indexer == null) {
						return false;
					}

					return true;
				});

		//assetEntryQuery.setClassNameIds(availableClassNameIds);
		assetEntryQuery.setClassName("com.liferay.journal.model.JournalArticle");

		assetEntryQuery.setEnablePermissions(true);


		Optional<Group> groupOptional =
				infoListProviderContext.getGroupOptional();

		if (groupOptional.isPresent()) {
			Group group = groupOptional.get();

			assetEntryQuery.setGroupIds(new long[] {group.getGroupId()});
		}

		if (pagination != null) {
			assetEntryQuery.setStart(pagination.getStart());
			assetEntryQuery.setEnd(pagination.getEnd());
		}

		assetEntryQuery.setOrderByCol1(orderByCol);
		assetEntryQuery.setOrderByType1(orderByType);

		assetEntryQuery.setOrderByCol2(Field.CREATE_DATE);
		assetEntryQuery.setOrderByType2("DESC");

		return assetEntryQuery;
	}

	@Reference
	protected Portal portal;

	private static final Log _log = LogFactoryUtil.getLog(
			UserRatedAssetsInfoListProvider.class);

	@Reference
	private AssetHelper _assetHelper;

	@Reference
	private RatingsEntryLocalService _RatingsEntryLocalService;

	@Reference(target = "(bundle.symbolic.name=com.liferay.asset.service)")
	private ResourceBundleLoader _resourceBundleLoader;

}