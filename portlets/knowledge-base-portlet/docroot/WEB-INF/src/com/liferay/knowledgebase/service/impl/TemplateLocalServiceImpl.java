/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.knowledgebase.service.impl;

import com.liferay.knowledgebase.NoSuchTemplateException;
import com.liferay.knowledgebase.TemplateContentException;
import com.liferay.knowledgebase.TemplateTitleException;
import com.liferay.knowledgebase.admin.social.AdminActivityKeys;
import com.liferay.knowledgebase.model.Template;
import com.liferay.knowledgebase.service.base.TemplateLocalServiceBaseImpl;
import com.liferay.knowledgebase.util.KnowledgeBaseUtil;
import com.liferay.portal.kernel.dao.orm.Conjunction;
import com.liferay.portal.kernel.dao.orm.Disjunction;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.Junction;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;

import java.util.Date;
import java.util.List;

/**
 * @author Peter Shin
 * @author Brian Wing Shun Chan
 */
public class TemplateLocalServiceImpl extends TemplateLocalServiceBaseImpl {

	public Template addTemplate(
			long userId, String title, String content, String description,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Template

		User user = userPersistence.findByPrimaryKey(userId);
		long groupId = serviceContext.getScopeGroupId();
		Date now = new Date();

		validate(title, content);

		long templateId = counterLocalService.increment();

		Template template = templatePersistence.create(templateId);

		template.setUuid(serviceContext.getUuid());
		template.setGroupId(groupId);
		template.setCompanyId(user.getCompanyId());
		template.setUserId(user.getUserId());
		template.setUserName(user.getFullName());
		template.setCreateDate(serviceContext.getCreateDate(now));
		template.setModifiedDate(serviceContext.getModifiedDate(now));
		template.setTitle(title);
		template.setContent(content);
		template.setDescription(description);

		templatePersistence.update(template, false);

		// Resources

		if (serviceContext.getAddCommunityPermissions() ||
			serviceContext.getAddGuestPermissions()) {

			addTemplateResources(
				template, serviceContext.getAddCommunityPermissions(),
				serviceContext.getAddGuestPermissions());
		}
		else {
			addTemplateResources(
				template, serviceContext.getCommunityPermissions(),
				serviceContext.getGuestPermissions());
		}

		// Social

		socialActivityLocalService.addActivity(
			userId, groupId, Template.class.getName(), templateId,
			AdminActivityKeys.ADD_TEMPLATE, StringPool.BLANK, 0);

		return template;
	}

	public void addTemplateResources(
			Template template, boolean addCommunityPermissions,
			boolean addGuestPermissions)
		throws PortalException, SystemException {

		resourceLocalService.addResources(
			template.getCompanyId(), template.getGroupId(),
			template.getUserId(), Template.class.getName(),
			template.getTemplateId(), false, addCommunityPermissions,
			addGuestPermissions);
	}

	public void addTemplateResources(
			Template template, String[] communityPermissions,
			String[] guestPermissions)
		throws PortalException, SystemException {

		resourceLocalService.addModelResources(
			template.getCompanyId(), template.getGroupId(),
			template.getUserId(), Template.class.getName(),
			template.getTemplateId(), communityPermissions, guestPermissions);
	}

	public void deleteGroupTemplates(long groupId)
		throws PortalException, SystemException {

		for (Template template : templatePersistence.findByGroupId(groupId)) {
			deleteTemplate(template);
		}
	}

	public void deleteTemplate(long templateId)
		throws PortalException, SystemException {

		Template template = templatePersistence.findByPrimaryKey(templateId);

		deleteTemplate(template);
	}

	public void deleteTemplate(Template template)
		throws PortalException, SystemException {

		// Template

		templatePersistence.remove(template);

		// Resources

		resourceLocalService.deleteResource(
			template.getCompanyId(), Template.class.getName(),
			ResourceConstants.SCOPE_INDIVIDUAL, template.getTemplateId());

		// Comment

		commentLocalService.deleteComments(
			Template.class.getName(), template.getTemplateId());

		// Social

		socialActivityLocalService.deleteActivities(
			Template.class.getName(), template.getTemplateId());
	}

	public void deleteTemplates(long[] templateIds)
		throws PortalException, SystemException {

		for (long templateId : templateIds) {
			Template template = null;

			try {
				template = templatePersistence.findByPrimaryKey(templateId);
			}
			catch (NoSuchTemplateException nste) {
				continue;
			}

			deleteTemplate(template);
		}
	}

	public List<Template> getGroupTemplates(
			long groupId, int start, int end,
			OrderByComparator orderByComparator)
		throws SystemException {

		return templatePersistence.findByGroupId(
			groupId, start, end, orderByComparator);
	}

	public int getGroupTemplatesCount(long groupId) throws SystemException {
		return templatePersistence.countByGroupId(groupId);
	}

	public List<Template> search(
			long groupId, String title, String content, Date startDate,
			Date endDate, boolean andOperator, int start, int end,
			OrderByComparator orderByComparator)
		throws SystemException {

		DynamicQuery dynamicQuery = buildDynamicQuery(
			groupId, title, content, startDate, endDate, andOperator);

		return dynamicQuery(dynamicQuery, start, end, orderByComparator);
	}

	public Template updateTemplate(
			long templateId, String title, String content, String description,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Template

		validate(title, content);

		Template template = templatePersistence.findByPrimaryKey(templateId);

		template.setModifiedDate(serviceContext.getModifiedDate(null));
		template.setTitle(title);
		template.setContent(content);
		template.setDescription(description);

		templatePersistence.update(template, false);

		// Resources

		if ((serviceContext.getCommunityPermissions() != null) ||
			(serviceContext.getGuestPermissions() != null)) {

			updateTemplateResources(
				template, serviceContext.getCommunityPermissions(),
				serviceContext.getGuestPermissions());
		}

		// Social

		socialActivityLocalService.addActivity(
			template.getUserId(), template.getGroupId(),
			Template.class.getName(), templateId,
			AdminActivityKeys.UPDATE_TEMPLATE, StringPool.BLANK, 0);

		return template;
	}

	public void updateTemplateResources(
			Template template, String[] communityPermissions,
			String[] guestPermissions)
		throws PortalException, SystemException {

		resourceLocalService.updateResources(
			template.getCompanyId(), template.getGroupId(),
			Template.class.getName(), template.getTemplateId(),
			communityPermissions, guestPermissions);
	}

	protected DynamicQuery buildDynamicQuery(
		long groupId, String title, String content, Date startDate,
		Date endDate, boolean andOperator) {

		Junction junction = null;

		if (andOperator) {
			junction = RestrictionsFactoryUtil.conjunction();
		}
		else {
			junction = RestrictionsFactoryUtil.disjunction();
		}

		if (Validator.isNotNull(title)) {
			Disjunction disjunction = RestrictionsFactoryUtil.disjunction();

			for (String s : KnowledgeBaseUtil.splitKeywords(title)) {
				String value = StringPool.PERCENT + s + StringPool.PERCENT;

				disjunction.add(RestrictionsFactoryUtil.ilike("title", value));
			}

			junction.add(disjunction);
		}

		if (Validator.isNotNull(content)) {
			Disjunction disjunction = RestrictionsFactoryUtil.disjunction();

			for (String s : KnowledgeBaseUtil.splitKeywords(content)) {
				String value = StringPool.PERCENT + s + StringPool.PERCENT;

				disjunction.add(
					RestrictionsFactoryUtil.ilike("content", value));
			}

			junction.add(disjunction);
		}

		if ((endDate != null) && (startDate != null)) {
			Disjunction disjunction = RestrictionsFactoryUtil.disjunction();

			String[] propertyNames = {"createDate", "modifiedDate"};

			for (String propertyName : propertyNames) {
				Property property = PropertyFactoryUtil.forName(propertyName);

				Conjunction conjunction = RestrictionsFactoryUtil.conjunction();

				conjunction.add(property.gt(startDate));
				conjunction.add(property.lt(endDate));

				disjunction.add(conjunction);
			}

			junction.add(disjunction);
		}

		DynamicQuery dynamicQuery = DynamicQueryFactoryUtil.forClass(
			Template.class, getClass().getClassLoader());

		if (groupId > 0) {
			Property property = PropertyFactoryUtil.forName("groupId");

			dynamicQuery.add(property.eq(groupId));
		}

		return dynamicQuery.add(junction);
	}

	protected void validate(String title, String content)
		throws PortalException {

		if (Validator.isNull(title)) {
			throw new TemplateTitleException();
		}

		if (Validator.isNull(content)) {
			throw new TemplateContentException();
		}
	}

}